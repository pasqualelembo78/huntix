// hub-probe.js — verifica read-only dello stato di login del Unity Hub via CDP.
// Apre il menu utente per rilevare email/Sign out, poi lo richiude.
// Uso: node hub-probe.js [porta]   (default 9222)
// Exit code: 0 = firmato, 1 = non firmato, 2 = Hub non raggiungibile.
// Stampa una riga JSON: {"signedIn":true,"email":"...","org":"..."}
const puppeteer = require('puppeteer-core');

const PORT = process.env.UNITY_HUB_CDP_PORT || process.argv[2] || '9222';
const URL = `http://localhost:${PORT}`;
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.connect({ browserURL: URL, defaultViewport: null, protocolTimeout: 10000 });
  let page = null;
  for (const t of browser.targets()) {
    if (t.type() === 'page') { page = await t.page(); break; }
  }
  if (!page) throw new Error('no page');

  let email = null;
  let signedOutVisible = false;

  try {
    await page.evaluate(() => {
      const el = [...document.querySelectorAll('[aria-label],[title]')].find(b =>
        (b.getAttribute('aria-label') || b.getAttribute('title') || '') === 'User menu');
      if (el) el.click();
      return !!el;
    });
    await sleep(900);
    const info = await page.evaluate(() => {
      const txt = document.body ? document.body.innerText : '';
      return {
        email: (txt.match(/[\w.+-]+@[\w-]+\.[\w.]+/g) || [])[0] || null,
        signOut: /sign out/i.test(txt),
      };
    });
    email = info.email;
    signedOutVisible = info.signOut;
    await page.keyboard.press('Escape').catch(() => {});
  } catch (e) { /* Hub page non accessibile */ }

  const signedIn = signedOutVisible && !!email;
  console.log(JSON.stringify({ signedIn, email, org: null }));
  try { await browser.disconnect(); } catch (e) {}
  process.exit(signedIn ? 0 : 1);
})().catch((e) => {
  console.log(JSON.stringify({ signedIn: false, email: null, org: null, error: e.message }));
  process.exit(2);
});
