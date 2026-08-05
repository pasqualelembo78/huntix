// hub-login.js — login automatico al Unity Hub (Unity ID) via CDP.
//
// Env:
//   UNITY_EMAIL            email Unity ID (obbligatoria)
//   UNITY_PASSWORD         password Unity ID (obbligatoria)
//   UNITY_HUB_CDP_PORT     porta CDP del Hub (default 9222)
//   UNITY_2FA_FILE         file dove l'utente scrive il codice OTP (default
//                          /tmp/opencode/unity-lic/2fa.txt)
//   UNITY_LOG_FILE         se impostato, append del log su file
// Exit code: 0 = login completato, 1 = errore (vedi stdout).
const puppeteer = require('puppeteer-core');
const fs = require('fs');

const EMAIL = process.env.UNITY_EMAIL || '';
const PASSWORD = process.env.UNITY_PASSWORD || '';
const PORT = process.env.UNITY_HUB_CDP_PORT || '9222';
const TFA_FILE = process.env.UNITY_2FA_FILE || '/tmp/opencode/unity-lic/2fa.txt';
const LOGFILE = process.env.UNITY_LOG_FILE || '';

const LOG = (m) => {
  const line = `[${new Date().toISOString().slice(11, 19)}] ${m}`;
  console.log(line);
  if (LOGFILE) { try { fs.appendFileSync(LOGFILE, line + '\n'); } catch (e) {} }
};
const sleep = ms => new Promise(r => setTimeout(r, ms));

function targetUrlOf(browser, target) {
  return target.url ? target.url() : (target._targetInfo ? target._targetInfo.url : '');
}

(async () => {
  if (!EMAIL || !PASSWORD) throw new Error('UNITY_EMAIL/UNITY_PASSWORD mancanti');
  const browser = await puppeteer.connect({ browserURL: `http://localhost:${PORT}`, defaultViewport: null });
  LOG('Connected to Hub browser.');

  let page = null;
  for (const t of browser.targets()) {
    if (t.type() === 'page') { page = await t.page(); break; }
  }
  if (!page) throw new Error('No main page target');
  LOG('Main page: ' + page.url());

  const signedIn = await page.evaluate(() => {
    const btns = [...document.querySelectorAll('button, a')];
    const btn = btns.find(b => /sign in/i.test((b.textContent || '').trim()));
    if (btn) { btn.click(); return 'clicked'; }
    return 'no-signin-btn';
  });
  LOG('Sign in click: ' + signedIn);
  await sleep(5000);

  let loginPage = null;
  for (let i = 0; i < 30 && !loginPage; i++) {
    for (const t of browser.targets()) {
      const u = targetUrlOf(browser, t);
      if (t.type() === 'page' && (u.includes('login.unity.com') || u.includes('license.unity3d.com'))) {
        loginPage = await t.page();
        break;
      }
    }
    if (!loginPage) await sleep(2000);
  }
  if (!loginPage) throw new Error('Login window not found');
  LOG('Login page: ' + loginPage.url());
  await loginPage.bringToFront();
  await sleep(2000);

  await loginPage.waitForSelector('#email', { timeout: 30000 });
  await loginPage.type('#email', EMAIL, { delay: 25 });
  await sleep(400);
  await loginPage.evaluate(() => {
    const btn = [...document.querySelectorAll('button')].find(b => (b.textContent || '').trim() === 'Continue');
    if (btn) btn.click();
  });
  await sleep(4000);

  const pwSel = await loginPage.$('input[type=password]');
  if (pwSel) {
    await pwSel.type(PASSWORD, { delay: 25 });
    await sleep(400);
    await loginPage.evaluate(() => {
      const btn = [...document.querySelectorAll('button')].find(b => /Sign in/i.test((b.textContent || '').trim()));
      if (btn) btn.click();
    });
    await sleep(6000);
  }
  LOG('URL after password: ' + loginPage.url());

  if (loginPage.url().includes('security-check')) {
    LOG('2FA REQUIRED: apri la mail e scrivi il codice in ' + TFA_FILE);
    await loginPage.evaluate(() => {
      const a = [...document.querySelectorAll('a')].find(x => /resend code/i.test((x.textContent || '').trim()));
      if (a) a.click();
    }).catch(() => {});
    await sleep(2000);
    const deadline = Date.now() + 15 * 60 * 1000;
    let code = null;
    while (Date.now() < deadline) {
      if (fs.existsSync(TFA_FILE)) {
        code = fs.readFileSync(TFA_FILE, 'utf8').trim();
        fs.rmSync(TFA_FILE);
        break;
      }
      await sleep(1500);
    }
    if (!code) throw new Error('Nessun codice 2FA fornito (file: ' + TFA_FILE + ')');
    LOG('Got code. Submitting...');
    await loginPage.evaluate((c) => {
      const el = document.querySelector('#one-time-code');
      if (el) {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(el, c);
        el.dispatchEvent(new Event('input', { bubbles: true }));
      }
    }, code);
    await sleep(800);
    await loginPage.evaluate(() => {
      const btn = [...document.querySelectorAll('button')].find(b => (b.textContent || '').trim() === 'Continue');
      if (btn) btn.click();
    });
    await sleep(8000);
    LOG('URL after 2FA: ' + loginPage.url());
  }

  LOG('Waiting for Hub to finish sign-in...');
  await sleep(8000);

  // Verifica finale tramite il menu utente
  await page.bringToFront();
  const state = await page.evaluate(() => {
    const el = [...document.querySelectorAll('[aria-label],[title]')].find(b =>
      (b.getAttribute('aria-label') || b.getAttribute('title') || '') === 'User menu');
    if (el) el.click();
    return !!el;
  });
  await sleep(1200);
  const email = await page.evaluate(() => {
    const m = document.body.innerText.match(/[\w.+-]+@[\w-]+\.[\w.]+/g);
    return m ? m[0] : null;
  });
  await page.keyboard.press('Escape');
  if (email) {
    LOG('LOGIN OK — account: ' + email);
    process.exit(0);
  }
  throw new Error('Login non confermato (nessun account nel menu utente)');
})().catch(e => { LOG('FATAL: ' + e.message); process.exit(1); });
