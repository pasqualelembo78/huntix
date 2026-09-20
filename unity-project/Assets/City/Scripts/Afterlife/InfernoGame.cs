using System.Collections.Generic;
using UnityEngine;
using TMPro;
using UnityEngine.UI;
using City.Player;

namespace City.Afterlife
{
    /// <summary>
    /// Minigioco "SCALA IL VULCANO" del regno INFERNO (3.4). La lava sale dal
    /// fondo del cratere: il player scala le piattaforme a spirale e deve
    /// raggiungere il portale d'oro in cima prima di essere travolto.
    /// Cadere nella lava o essere colpito da una sfera di fuoco costa una vita
    /// (3 vite, respawn sull'ultimo checkpoint con invulnerabilita' breve);
    /// i checkpoint sono piattaforme segnalate da una fiammella dorata.
    /// Vittoria (portale) = Inferno superato + XP; 0 vite = Inferno fallito;
    /// in ogni caso si avanza al Purgatorio tramite
    /// RealmSceneManager.InfernoFinished. Il player usa le meccaniche Inferno
    /// (doppio salto + dash) gia' attive in PlayerController.
    /// </summary>
    public class InfernoGame : MonoBehaviour
    {
        private const float StepHeight = 1.2f;
        private const int PlatformCount = 13;
        private const int CheckpointEvery = 3;
        private const int MaxLives = 3;
        private const float StartRadius = 2.5f;
        private const float RadiusStep = 0.55f;
        private const float AngleStep = 0.5f;
        private const float LavaStartY = -1.8f;
        private const float LavaSpeed = 0.3f;
        private const float LavaGrace = 3f;
        private const float InvulnTime = 2f;
        private const float PlatformSize = 2.4f;
        private const float PlatformThick = 0.5f;

        private RealmSceneController ctrl;
        private GameObject arena;
        private Canvas hud;
        private TextMeshProUGUI titleText;
        private TextMeshProUGUI statusText;
        private TextMeshProUGUI livesText;
        private TextMeshProUGUI lavaText;
        private TextMeshProUGUI hintText;
        private TextMeshProUGUI progressText;

        private Transform[] platforms;
        private float[] topY;
        private Transform portal;
        private Transform lavaSlab;
        private Renderer lavaRend;

        private float lavaY;
        private float elapsed;
        private int lives;
        private int checkpoint;
        private float invulnUntil;
        private bool active;

        private float resultWait;
        private bool resultWon;

        private float nextFireball;
        private readonly List<Fireball> fireballs = new List<Fireball>();

        private class Fireball
        {
            public Transform transform;
            public Vector3 velocity;
        }

        private Color LavaDark = new Color(0.85f, 0.22f, 0.03f);
        private Color LavaBright = new Color(1f, 0.55f, 0.08f);
        private float pulsePhase;

        /// <summary>Crea e avvia il minigioco nell'arena del regno INFERNO.</summary>
        public static InfernoGame Create(RealmSceneController ctrl)
        {
            var ig = ctrl.gameObject.AddComponent<InfernoGame>();
            ig.ctrl = ctrl;
            ig.Setup();
            return ig;
        }

        private void Setup()
        {
            arena = new GameObject("InfernoArena");
            arena.transform.SetParent(ctrl.ArenaRoot, false);

            BuildPlatforms();
            BuildLava();
            BuildPortal();
            BuildHud();

            lives = MaxLives;
            checkpoint = 0;
            nextFireball = 6f;
            lavaY = LavaStartY;
            UpdateLavaVisual(0f);
            active = true;

            PlaceOnPlatform(0);
            ShowStatus("VIA!", new Color(1f, 0.85f, 0.4f));
            UpdateHud();
        }

        // ── Costruzione dell'arena ─────────────────────────────────

        private void BuildPlatforms()
        {
            platforms = new Transform[PlatformCount];
            topY = new float[PlatformCount];
            for (int i = 0; i < PlatformCount; i++)
            {
                Vector3 pos;
                if (i == 0)
                {
                    pos = new Vector3(0f, -PlatformThick * 0.5f, 0f);
                }
                else
                {
                    float ang = (i - 1) * AngleStep;
                    float rad = StartRadius + (i - 1) * RadiusStep;
                    pos = new Vector3(Mathf.Cos(ang) * rad, i * StepHeight - PlatformThick * 0.5f,
                                       Mathf.Sin(ang) * rad);
                }

                var p = GameObject.CreatePrimitive(PrimitiveType.Cube);
                p.name = "Platform" + i;
                p.transform.SetParent(arena.transform, false);
                p.transform.position = pos;
                float size = (i == 0) ? PlatformSize + 0.8f : PlatformSize;
                p.transform.localScale = new Vector3(size, PlatformThick, size);

                var rend = p.GetComponent<Renderer>();
                if (rend != null)
                {
                    rend.material.color = (i % CheckpointEvery == 0)
                        ? new Color(0.85f, 0.55f, 0.2f)
                        : new Color(0.42f, 0.1f, 0.05f);
                }

                platforms[i] = p.transform;
                topY[i] = pos.y + PlatformThick * 0.5f;

                if (i % CheckpointEvery == 0)
                    BuildBeacon(p.transform, topY[i]);
            }
        }

        private void BuildBeacon(Transform plat, float top)
        {
            var b = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            b.name = "Beacon";
            b.transform.SetParent(plat, false);
            b.transform.localPosition = new Vector3(0f, 0.9f, 0f);
            b.transform.localScale = new Vector3(0.4f, 0.8f, 0.4f);
            var r = b.GetComponent<Renderer>();
            if (r != null) r.material.color = new Color(1f, 0.85f, 0.3f);
            var col = b.GetComponent<Collider>();
            if (col != null) col.isTrigger = true;
        }

        private void BuildLava()
        {
            var l = GameObject.CreatePrimitive(PrimitiveType.Cube);
            l.name = "Lava";
            l.transform.SetParent(arena.transform, false);
            l.transform.localScale = new Vector3(30f, 1f, 30f);
            lavaRend = l.GetComponent<Renderer>();
            if (lavaRend != null) lavaRend.material.color = LavaBright;
            var c = l.GetComponent<Collider>();
            if (c != null) c.isTrigger = true;
            lavaSlab = l.transform;
        }

        private void BuildPortal()
        {
            var top = platforms[PlatformCount - 1];
            var p = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            p.name = "Portal";
            p.transform.SetParent(arena.transform, false);
            p.transform.position = top.position + new Vector3(0f, topY[PlatformCount - 1] + 1f, 0f);
            p.transform.localScale = new Vector3(1.8f, 1.6f, 1.8f);
            var r = p.GetComponent<Renderer>();
            if (r != null) r.material.color = new Color(1f, 0.9f, 0.35f);
            var c = p.GetComponent<Collider>();
            if (c != null) c.isTrigger = true;
            portal = p.transform;
        }

        // ── HUD ───────────────────────────────────────────────────

        private void BuildHud()
        {
            var hudGo = new GameObject("InfernoHud");
            hudGo.transform.SetParent(ctrl.ArenaRoot, false);
            hud = hudGo.AddComponent<Canvas>();
            hud.renderMode = RenderMode.ScreenSpaceOverlay;
            hud.sortingOrder = 910;
            var scaler = hudGo.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);

            titleText = MakeText(hud.transform, "SCALA IL VULCANO", new Vector2(0, 650),
                52, new Color(1f, 0.6f, 0.15f));
            statusText = MakeText(hud.transform, "VIA!", new Vector2(0, 120),
                72, new Color(1f, 0.85f, 0.4f));
            livesText = MakeText(hud.transform, "VITE: 3", new Vector2(-420, 600),
                42, Color.white);
            lavaText = MakeText(hud.transform, "LAVA: 0%", new Vector2(360, 600),
                34, new Color(1f, 0.5f, 0.05f));
            progressText = MakeText(hud.transform, "", new Vector2(0, 200),
                40, new Color(1f, 1f, 1f, 0.95f));
            hintText = MakeText(hud.transform,
                "SALTA TRA LE PIATTAFORME. RAGGIUNGI IL PORTALE D'ORO!",
                new Vector2(0, -640), 34, new Color(1f, 1f, 1f, 0.9f));
        }

        private static TextMeshProUGUI MakeText(Transform parent, string t, Vector2 pos,
            float size, Color c)
        {
            var go = new GameObject("Txt");
            go.transform.SetParent(parent, false);
            var txt = go.AddComponent<TextMeshProUGUI>();
            txt.alignment = TextAlignmentOptions.Center;
            txt.text = t;
            txt.fontSize = size;
            txt.color = c;
            var rt = txt.rectTransform;
            rt.anchoredPosition = pos;
            rt.sizeDelta = new Vector2(1000, 100);
            return txt;
        }

        private void ShowStatus(string msg, Color c)
        {
            if (statusText == null) return;
            statusText.text = msg;
            statusText.color = c;
        }

        private void UpdateHud()
        {
            if (livesText != null)
                livesText.text = "VITE: " + Mathf.Max(0, lives);
            float lastTop = topY[PlatformCount - 1];
            float pct = Mathf.Clamp01((lavaY - LavaStartY) / (lastTop - LavaStartY));
            if (lavaText != null)
                lavaText.text = "LAVA: " + Mathf.RoundToInt(pct * 100f) + "%";
            if (progressText != null)
            {
                float p = Mathf.Clamp01(elapsed / 60f);
                int pctReached = Mathf.RoundToInt(p * 100f);
                progressText.text = "SALITA: " + pctReached + "%  |  TEMPO: " + Mathf.RoundToInt(elapsed) + "s";
            }
        }

        // ── Gioco ─────────────────────────────────────────────────

        private void Update()
        {
            // pausa finale prima di consegnare il risultato (anche a minigioco concluso)
            if (resultWait > 0f)
            {
                resultWait -= Time.unscaledDeltaTime;
                if (resultWait <= 0f) DeliverResult();
                return;
            }

            if (!active) return;

            var rsm = RealmSceneManager.Instance;
            if (rsm == null || rsm.ActiveRealmId != AfterlifeRealm.INFERNO)
            {
                // regno cambiato dall'esterno: il minigioco si mette in pausa
                active = false;
                return;
            }

            elapsed += Time.deltaTime;

            UpdateLava();
            TrackCheckpoint();
            CheckLavaDamage();
            CheckPortalWin();
            UpdateFireballs();
            UpdateBeacons();
            UpdateHud();
        }

        private void UpdateLava()
        {
            if (elapsed < LavaGrace) return;
            lavaY += LavaSpeed * Time.deltaTime;
            UpdateLavaVisual(elapsed);
            // la lava ha coperto tutto: se il player non ha ancora vinto, perde
            if (lavaY > topY[PlatformCount - 1] + 0.5f) LoseLife();
        }

        private void UpdateLavaVisual(float time)
        {
            if (lavaSlab == null) return;
            lavaSlab.position = new Vector3(0f, lavaY, 0f);
            if (lavaRend != null)
            {
                pulsePhase += Time.deltaTime * 5f;
                Color c = Color.Lerp(LavaDark, LavaBright,
                    Mathf.Sin(pulsePhase * Mathf.PI * 2f) * 0.5f + 0.5f);
                lavaRend.material.color = c;
            }
        }

        private void TrackCheckpoint()
        {
            var pc = PlayerController.Instance;
            if (pc == null) return;
            Vector3 pos = pc.transform.position;
            for (int i = 0; i < PlatformCount; i++)
            {
                if (i % CheckpointEvery != 0) continue;
                float dy = Mathf.Abs(pos.y - (topY[i] + 0.5f));
                if (dy > 1.6f) continue;
                Vector3 d = pos - platforms[i].position;
                d.y = 0f;
                if (d.magnitude < 1.5f && i > checkpoint)
                {
                    checkpoint = i;
                    ShowStatus("CHECKPOINT!", new Color(0.3f, 1f, 0.45f));
                }
            }
        }

        private void CheckLavaDamage()
        {
            if (Time.unscaledTime < invulnUntil) return;
            var pc = PlayerController.Instance;
            if (pc == null) return;
            float feet = pc.transform.position.y - 0.9f;
            if (feet < lavaY + 0.15f) LoseLife();
        }

        private void CheckPortalWin()
        {
            var pc = PlayerController.Instance;
            if (pc == null) return;
            Vector3 d = pc.transform.position - portal.position;
            d.y = 0f;
            float lastTop = topY[PlatformCount - 1];
            if (d.magnitude < 2.2f && pc.transform.position.y > lastTop + 0.2f)
                StartResult(true);
        }

        private void LoseLife()
        {
            if (!active || Time.unscaledTime < invulnUntil) return;
            lives--;
            if (lives <= 0)
            {
                StartResult(false);
                return;
            }
            invulnUntil = Time.unscaledTime + InvulnTime;
            PlaceOnPlatform(RespawnIndex());
            ShowStatus("FUOCO! -1 VITA", new Color(1f, 0.3f, 0.2f));
            UpdateHud();
        }

        /// <summary>Indice del checkpoint piu' alto ancora sopra la lava;
        /// fallback: ultimo checkpoint raggiunto.</summary>
        private int RespawnIndex()
        {
            int best = checkpoint;
            for (int i = 0; i < PlatformCount; i++)
            {
                if (i % CheckpointEvery != 0) continue;
                if (topY[i] > lavaY + 0.5f && i > best) best = i;
            }
            return best;
        }

        private void PlaceOnPlatform(int i)
        {
            var pc = PlayerController.Instance;
            if (pc == null) return;
            var cc = pc.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            Vector3 platPos = i < platforms.Length ? platforms[i].position : Vector3.zero;
            pc.transform.position = new Vector3(platPos.x, topY[i] + 0.5f, platPos.z);
            pc.transform.rotation = Quaternion.identity;
            if (cc != null) cc.enabled = true;
            pc.Stop();
        }

        private void StartResult(bool won)
        {
            if (!active) return;
            active = false;
            resultWon = won;
            resultWait = 2.6f;
            if (won)
                ShowStatus("PORTALE RAGGIUNTO!", new Color(0.4f, 1f, 0.5f));
            else
                ShowStatus("INFERNO VINTO...", new Color(1f, 0.35f, 0.2f));
        }

        private void DeliverResult()
        {
            var rsm = RealmSceneManager.Instance;
            if (rsm != null && rsm.InfernoFinished != null)
                rsm.InfernoFinished(resultWon);
        }

        // ── Pericoli: sfere di fuoco ───────────────────────────────

        private void UpdateFireballs()
        {
            if (elapsed < 5f) return;
            if (elapsed >= nextFireball)
            {
                nextFireball = elapsed + Random.Range(4f, 7f);
                SpawnFireball();
            }

            if (fireballs.Count == 0) return;
            var pc = PlayerController.Instance;
            for (int i = fireballs.Count - 1; i >= 0; i--)
            {
                Fireball fb = fireballs[i];
                fb.transform.position += fb.velocity * Time.deltaTime;
                if (fb.transform.position.magnitude > 16f || fb.transform.position.y < -3f)
                {
                    Destroy(fb.transform.gameObject);
                    fireballs.RemoveAt(i);
                    continue;
                }
                if (pc != null)
                {
                    Vector3 d = fb.transform.position - pc.transform.position;
                    d.y *= 0.5f;
                    if (d.sqrMagnitude < 1.6f)
                    {
                        Vector3 away = (pc.transform.position - fb.transform.position);
                        away.y = 0f;
                        if (away.sqrMagnitude < 0.1f) away = Vector3.right;
                        away.Normalize();
                        pc.ApplyKnockback(away * 8f);
                        ShowStatus("SFERA DI FUOCO!", new Color(1f, 0.5f, 0.1f));
                        Destroy(fb.transform.gameObject);
                        fireballs.RemoveAt(i);
                    }
                }
            }
        }

        private void SpawnFireball()
        {
            var pc = PlayerController.Instance;
            float ang = Random.Range(0f, Mathf.PI * 2f);
            float r = 7f;
            Vector3 start = new Vector3(Mathf.Cos(ang) * r, lavaY + 0.6f, Mathf.Sin(ang) * r);

            var sphere = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            sphere.name = "Fireball";
            sphere.transform.SetParent(arena.transform, false);
            sphere.transform.position = start;
            sphere.transform.localScale = new Vector3(0.8f, 0.8f, 0.8f);
            var rend = sphere.GetComponent<Renderer>();
            if (rend != null) rend.material.color = new Color(1f, 0.5f, 0.05f);
            var col = sphere.GetComponent<Collider>();
            if (col != null) col.isTrigger = true;

            // direzione: verso il centro, sovrapposta a uno sconto casuale
            Vector3 target = new Vector3(0f, lavaY + 3f, 0f);
            if (pc != null) target = new Vector3(pc.transform.position.x, lavaY + 3f, pc.transform.position.z);
            Vector3 dir = (target - start).normalized;
            dir += new Vector3(Random.Range(-0.4f, 0.4f), 0f, Random.Range(-0.4f, 0.4f));
            dir.Normalize();

            var fb = new Fireball();
            fb.transform = sphere.transform;
            fb.velocity = dir * Random.Range(4f, 5.5f);
            fireballs.Add(fb);
        }

        private void UpdateBeacons()
        {
            // fiammelle dorate sui checkpoint: pulsano morbide
            for (int i = 0; i < platforms.Length; i++)
            {
                if (i % CheckpointEvery != 0) continue;
                Transform beacon = platforms[i].Find("Beacon");
                if (beacon == null) continue;
                float f = Mathf.Sin(Time.time * 3f + i) * 0.25f + 1f;
                beacon.localScale = new Vector3(0.4f * f, 0.8f, 0.4f * f);
            }
        }
    }
}