using System;
using System.Collections;
using System.Collections.Generic;
using TMPro;
using UnityEngine;
using UnityEngine.UI;
using City.World;

namespace City.Environment
{
    /// <summary>
    /// Oggetto interattivo del mondo (panchina, fontanella, cestino, bar,
    /// farmacia, ATM): etichetta a schermo quando vicini e azione al tap.
    /// Il tap viene instradato da TouchInputHandler prima dei pedoni.
    /// </summary>
    public class InteractableProp : MonoBehaviour
    {
        public enum Kind { Bench, Fountain, Bin, Cafe, Pharmacy, Atm }

        public Kind kind;
        public string title = "";
        public string action = "";

        // ── caos urbano ──
        /// <summary>Tutti i prop attivi (per il rilevatore di calci).</summary>
        public static readonly List<InteractableProp> All =
            new List<InteractableProp>();

        /// <summary>Vetrina associata (solo POI commerciali).</summary>
        public BreakableWindow Window;

        /// <summary>Già rovesciato dal player.</summary>
        public bool Kicked { get; private set; }

        /// <summary>Velocita' minima del player per calcare questo prop.</summary>
        public float KickSpeedNeeded
        {
            get { return kind == Kind.Bench ? 4.4f : 3.2f; }
        }

        private Coroutine _tipCo;

        private void OnEnable()
        {
            if (!All.Contains(this)) All.Add(this);
        }

        private void OnDisable()
        {
            All.Remove(this);
        }

        /// <summary>Calcio: il prop si rovescia nella direzione spinta.</summary>
        public void Kick(Vector3 pushDir)
        {
            if (Kicked) return;
            if (kind == Kind.Fountain)
            {
                Toast("\u26fd Salda come una roccia: la fontanella non si muove");
                return;
            }
            Kicked = true;
            if (kind == Kind.Bin) SpawnLitter(pushDir);
            ChaosTracker.AddChaos(kind == Kind.Bench ? 2 : 1);
            if (_tipCo != null) StopCoroutine(_tipCo);
            _tipCo = StartCoroutine(TipOver(pushDir));
        }

        private IEnumerator TipOver(Vector3 dir)
        {
            dir.y = 0f;
            if (dir.sqrMagnitude < 0.001f) dir = Vector3.forward;
            dir.Normalize();
            Vector3 axis = Vector3.Cross(Vector3.up, dir);
            Vector3 pivot = transform.position + dir * 0.35f;
            pivot.y = Mathf.Max(0f, transform.position.y - 0.35f);

            const float totalDeg = 82f;
            float done = 0f;
            while (done < totalDeg)
            {
                float step = 340f * Time.deltaTime;
                if (done + step > totalDeg) step = totalDeg - done;
                transform.RotateAround(pivot, axis, step);
                done += step;
                yield return null;
            }
            // piccolo rimbalzo di assestamento
            const float backDeg = 6f;
            float back = 0f;
            while (back < backDeg)
            {
                float step = 120f * Time.deltaTime;
                if (back + step > backDeg) step = backDeg - back;
                transform.RotateAround(pivot, axis, -step);
                back += step;
                yield return null;
            }
        }

        private void SpawnLitter(Vector3 pushDir)
        {
            int n = 3 + UnityEngine.Random.Range(0, 3);
            for (int i = 0; i < n; i++)
            {
                var c = GameObject.CreatePrimitive(PrimitiveType.Cube);
                UnityEngine.Object.Destroy(c.GetComponent<Collider>());
                Transform par = transform.parent;
                if (par != null) c.transform.SetParent(par, true);
                Vector2 r = UnityEngine.Random.insideUnitCircle * 0.8f;
                c.transform.position = transform.position +
                    new Vector3(r.x + pushDir.x * 0.5f, 0.06f,
                                r.y + pushDir.z * 0.5f);
                c.transform.rotation = UnityEngine.Random.rotation;
                c.transform.localScale = new Vector3(0.14f, 0.12f, 0.14f);
                Tint(c, new Color(0.45f, 0.36f, 0.25f));
            }
            int clean = PlayerPrefs.GetInt("city_clean_count", 0);
            PlayerPrefs.SetInt("city_clean_count", Mathf.Max(0, clean - n));
            PlayerPrefs.Save();
            Toast("\ud83d\uddd1\ufe0f Rifiuti dappertutto! La citta' e' meno pulita...");
        }

        internal static void Tint(GameObject go, Color c)
        {
            var r = go.GetComponent<Renderer>();
            if (r == null) return;
            var mats = r.sharedMaterials;
            for (int i = 0; i < mats.Length; i++)
            {
                Material m = new Material(Shader.Find("Sprites/Default"));
                m.color = c;
                mats[i] = m;
            }
            r.sharedMaterials = mats;
        }

        private TextMeshPro _label;

        // ── routing dal tap ─────────────────────────────────────

        /// <summary>Prop colpito dal raycast, null se non c'e'.</summary>
        public static InteractableProp FromHit(GameObject hitGo)
        {
            if (hitGo == null) return null;
            return hitGo.GetComponentInParent<InteractableProp>();
        }

        // ── azioni ──────────────────────────────────────────────

        public void Interact()
        {
            switch (kind)
            {
                case Kind.Bench:
                    if (SitController.IsSitting) SitController.StandUp();
                    else SitController.Sit(
                        City.Game.Instance != null && City.Game.Instance.player != null
                            ? City.Game.Instance.player.transform : null,
                        transform.position, transform.rotation);
                    break;

                case Kind.Fountain:
                    EnergySystem.Restore(8);
                    Toast("\u26fd Bevi alla fontanella: +8 energia");
                    break;

                case Kind.Bin:
                    int n = PlayerPrefs.GetInt("city_clean_count", 0) + 1;
                    PlayerPrefs.SetInt("city_clean_count", n);
                    PlayerPrefs.Save();
                    Toast("\ud83d\uddd1\ufe0f Citta' piu' pulita! (" + n + " rifiuti smaltiti)");
                    break;

                case Kind.Cafe:
                    ChoicePanel.Show(title, new ChoicePanel.Option[]
                    {
                        new ChoicePanel.Option("\u2615 Caff\u00e8 \u2014 2\u20ac (+15\u26a1)", () =>
                        {
                            if (!Wallet.TrySpend(2)) { Toast("Soldi insufficienti"); return; }
                            EnergySystem.Restore(15);
                            Toast("\u2615 Che botta di caffe': +15 energia");
                        }),
                        new ChoicePanel.Option("\ud83c\udf79 Aperitivo \u2014 5\u20ac (+30\u26a1)", () =>
                        {
                            if (!Wallet.TrySpend(5)) { Toast("Soldi insufficienti"); return; }
                            EnergySystem.Restore(30);
                            Toast("\ud83c\udf79 Aperitivo riuscito: +30 energia");
                        }),
                    });
                    break;

                case Kind.Pharmacy:
                    ChoicePanel.Show(title, new ChoicePanel.Option[]
                    {
                        new ChoicePanel.Option("\ud83d\udc8a Kit pronto soccorso \u2014 8\u20ac (+25\u26a1)", () =>
                        {
                            if (!Wallet.TrySpend(8)) { Toast("Soldi insufficienti"); return; }
                            EnergySystem.Restore(25);
                            Toast("\ud83d\udc8a Kit comprato: +25 energia");
                        }),
                    });
                    break;

                case Kind.Atm:
                    AtmPanel();
                    break;
            }
        }

        private void AtmPanel()
        {
            const string bankKey = "city_bank";
            ChoicePanel.Show("🏧 ATM \u00b7 Banca",
                new ChoicePanel.Option[]
                {
                    new ChoicePanel.Option("\u2b07\ufe0f Deposita tutto (" +
                        Wallet.Money + "\u20ac)", () =>
                    {
                        PlayerPrefs.SetInt(bankKey,
                            PlayerPrefs.GetInt(bankKey, 0) + Wallet.Money);
                        Wallet.Spend(Wallet.Money);
                        Toast("Deposito effettuato. Saldo banca: " +
                            PlayerPrefs.GetInt(bankKey, 0) + "\u20ac");
                    }),
                    new ChoicePanel.Option("\u2b06\ufe0f Preleva tutto (" +
                        PlayerPrefs.GetInt(bankKey, 0) + "\u20ac)", () =>
                    {
                        int b = PlayerPrefs.GetInt(bankKey, 0);
                        if (b <= 0) { Toast("Conto vuoto"); return; }
                        Wallet.Earn(b);
                        PlayerPrefs.SetInt(bankKey, 0);
                        Toast("Prelevati " + b + "\u20ac");
                    }),
                },
                "Wallet: " + Wallet.Money + "\u20ac \u00b7 Conto: " +
                PlayerPrefs.GetInt(bankKey, 0) + "\u20ac");
        }

        private void Toast(string msg)
        {
            if (City.Game.Instance != null && City.Game.Instance.ui != null)
                City.Game.Instance.ui.ShowToast(msg);
        }

        // ── etichetta mondo ─────────────────────────────────────

        private void Start()
        {
            var go = new GameObject("PropLabel");
            go.transform.SetParent(transform, false);
            go.transform.localPosition = new Vector3(0f, 2.1f, 0f);
            var rt = go.AddComponent<RectTransform>();
            rt.sizeDelta = new Vector2(6f, 1.6f);
            _label = go.AddComponent<TextMeshPro>();
            _label.fontSize = 2.2f;
            _label.alignment = TextAlignmentOptions.Center;
            _label.color = Color.white;
            _label.text = title + (string.IsNullOrEmpty(action)
                ? "" : "\n<size=60%>" + action + "</size>");
            go.SetActive(false);
            EnergySystem.EnsureHud();
        }

        private void LateUpdate()
        {
            if (_label == null) return;
            var cam = Camera.main;
            if (cam == null) return;
            bool show = (cam.transform.position - transform.position)
                .sqrMagnitude < 225f;   // 15 m
            if (show)
            {
                if (!_label.gameObject.activeSelf) _label.gameObject.SetActive(true);
                _label.transform.rotation = cam.transform.rotation;
            }
            else if (_label.gameObject.activeSelf)
                _label.gameObject.SetActive(false);
        }
    }

    /// <summary>
    /// Mini-pannello di scelta generico (menu bar, farmacia, ATM):
    /// righe con azione + pulsante chiudi. Costruito al volo sulla canvas.
    /// </summary>
    public class ChoicePanel
    {
        public class Option
        {
            public string label;
            public Action onPick;
            public Option(string label, Action onPick)
            {
                this.label = label;
                this.onPick = onPick;
            }
        }

        private static GameObject _open;

        public static void Show(string titleText, Option[] options)
        {
            Show(titleText, options, null);
        }

        public static void Show(string titleText, Option[] options,
            string footer)
        {
            Close();

            var canvas = UnityEngine.Object.FindObjectOfType<Canvas>();
            if (canvas == null) return;

            _open = new GameObject("ChoicePanel");
            var prt = _open.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = prt.anchorMax = prt.pivot = new Vector2(0.5f, 0.5f);
            float h = 90f + options.Length * 64f + (footer != null ? 40f : 0f);
            prt.offsetMin = new Vector2(-250f, -h * 0.5f);
            prt.offsetMax = new Vector2(250f, h * 0.5f);
            var bg = _open.AddComponent<Image>();
            bg.color = new Color(0.11f, 0.12f, 0.14f, 0.97f);

            MakeText(prt, titleText, 28f, Color.white, TextAlignmentOptions.Left,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(16f, -12f), new Vector2(-16f, -52f));

            if (footer != null)
                MakeText(prt, "<size=70%>" + footer + "</size>", 22f,
                    new Color(0.7f, 0.7f, 0.7f), TextAlignmentOptions.Left,
                    new Vector2(0f, 0f), new Vector2(1f, 0f),
                    new Vector2(16f, 10f), new Vector2(-16f, 44f));

            float y = -(footer != null ? 96f : 62f);
            foreach (var opt in options)
            {
                Option captured = opt;
                var brt = MakeButton(prt, opt.label, () =>
                {
                    bool reopen = _open != null;
                    Close();
                    captured.onPick();
                    // il pannello puo' voler restare aperto per altre scelte:
                    // qui chiudiamo sempre (interazioni rapide)
                    if (reopen) { /* chiuso dopo l'azione */ }
                }, new Color(0.20f, 0.22f, 0.25f, 1f));
                var brect = brt;
                brect.anchorMin = new Vector2(0f, 1f);
                brect.anchorMax = new Vector2(1f, 1f);
                brect.pivot = new Vector2(0.5f, 1f);
                brect.offsetMin = new Vector2(14f, y - 54f);
                brect.offsetMax = new Vector2(-14f, y);
                y -= 64f;
            }

            var close = MakeButton(prt, "Chiudi", Close,
                new Color(0.28f, 0.30f, 0.34f, 1f));
            close.anchorMin = new Vector2(0f, 0f);
            close.anchorMax = new Vector2(1f, 0f);
            close.pivot = new Vector2(0.5f, 0f);
            close.offsetMin = new Vector2(14f, 10f);
            close.offsetMax = new Vector2(-14f, 56f);
        }

        public static void Close()
        {
            if (_open != null) UnityEngine.Object.Destroy(_open);
            _open = null;
        }

        private static TMP_FontAsset Font()
        {
            var f = TMP_Settings.defaultFontAsset;
            if (f == null) f = Resources.Load<TMP_FontAsset>(
                "Fonts & Materials/LiberationSans SDF");
            return f;
        }

        private static RectTransform MakeRect(Transform parent,
            Vector2 anchorMin, Vector2 anchorMax,
            Vector2 offsetMin, Vector2 offsetMax)
        {
            var go = new GameObject("R", typeof(RectTransform));
            var rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin;
            rt.offsetMax = offsetMax;
            return rt;
        }

        private static TMP_Text MakeText(RectTransform parent, string content,
            float size, Color color, TextAlignmentOptions alignment,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var rt = MakeRect(parent, anchorMin, anchorMax, offsetMin, offsetMax);
            var t = rt.gameObject.AddComponent<TextMeshProUGUI>();
            t.text = content;
            t.fontSize = size;
            t.color = color;
            t.alignment = alignment;
            t.font = Font();
            t.raycastTarget = false;
            return t;
        }

        private static RectTransform MakeButton(RectTransform parent,
            string label, Action onClick, Color bg)
        {
            var rt = MakeRect(parent, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = bg;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(() => onClick());
            var t = MakeText(rt, label, 24f, Color.white,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
            t.alignment = TextAlignmentOptions.Center;
            return rt;
        }
    }
}
