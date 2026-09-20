def read(p):
    with open(p, encoding="utf-8") as f: return f.read()
def write(p, s):
    with open(p, "w", encoding="utf-8") as f: f.write(s)

def a(s, old, new, tag):
    if new in s:
        print("  =", tag, "(gia applicato)")
        return s
    assert s.count(old) == 1, tag + " (introvabile/multi)"
    s = s.replace(old, new)
    print("  +", tag)
    return s

base = "/root/giochi/huntix/unity-project/Assets/City/Scripts"

# ── 1) CameraRig.cs: giroscopio (yaw da rotationRate.y) + toggle + persistenza ──
p = base + "/Player/CameraRig.cs"
s = read(p)
s = a(s,
    "        public float orbitSpeed = 5f;\n",
    "        public float orbitSpeed = 5f;\n"
    "        // Giroscopio (Android): gira la visuale ruotando il telefono, in\n"
    "        // aggiunta al drag sul lato destro. Se ruota al contrario inverti\n"
    "        // il segno di GyroSensitivity. Il toggle GIRO in HUD lo spegne.\n"
    "        public const float GyroSensitivity = 57f;   // rad/s -> gradi (1:1 con il mondo)\n"
    "        public const float GyroDeadzone = 0.005f;   // ~0.3 gradi/s: ignora i tremori\n"
    "        public const float GyroSmoothing = 0.25f;\n"
    "        private bool _gyroEnabled = true;\n"
    "        private float _gyroRateEma;\n"
    "        public bool GyroEnabled { get { return _gyroEnabled; } }\n",
    "cam gyro fields")
s = a(s,
    "            if (target != null) yaw = target.eulerAngles.y;\n",
    "            if (target != null) yaw = target.eulerAngles.y;\n"
    "            if (SystemInfo.supportsGyroscope)\n"
    "            {\n"
    "                _gyroEnabled = PlayerPrefs.GetInt(\"gyroCam\", 1) == 1;\n"
    "                SetGyroSensor(_gyroEnabled);\n"
    "                OsmDiag.Log(\"[Camera][Gyro] supportato, attivo=\" + _gyroEnabled);\n"
    "            }\n"
    "            else { _gyroEnabled = false; }\n",
    "cam gyro start")
s = a(s,
    "        private void Update()\n        {\n            if (drivingMode) return;\n            HandlePinchZoom();\n        }\n",
    "        private void Update()\n        {\n            ApplyGyroYaw();\n            if (drivingMode) return;\n            HandlePinchZoom();\n        }\n\n"
    "        /// <summary>Ruota la visuale con il giroscopio: integra la velocita'\n"
    "        /// angolare attorno all'asse Y del device (girare il telefono come\n"
    "        /// quando lo si tiene in verticale ruotando il busto). Effetto\n"
    "        /// proporzionale-posizionale come il drag, quindi nessun salto ne'\n"
    "        /// deriva: si accumula mentre lo giri e resta dov'e' quando fermi.</summary>\n"
    "        private void ApplyGyroYaw()\n"
    "        {\n"
    "            if (!_gyroEnabled || !SystemInfo.supportsGyroscope) return;\n"
    "            float raw;\n"
    "            try { raw = Input.gyro.rotationRateUnbiased.y; }\n"
    "            catch (System.Exception) { return; }\n"
    "            if (Mathf.Abs(raw) < GyroDeadzone) raw = 0f;\n"
    "            _gyroRateEma = Mathf.Lerp(_gyroRateEma, raw, GyroSmoothing);\n"
    "            yaw += _gyroRateEma * GyroSensitivity * Time.deltaTime;\n"
    "        }\n\n"
    "        public void SetGyroEnabled(bool on)\n"
    "        {\n"
    "            _gyroEnabled = on && SystemInfo.supportsGyroscope;\n"
    "            SetGyroSensor(_gyroEnabled);\n"
    "            OsmDiag.Log(\"[Camera][Gyro] set -> \" + _gyroEnabled);\n"
    "        }\n\n"
    "        private void SetGyroSensor(bool on)\n"
    "        {\n"
    "            try { Input.gyro.enabled = on; }\n"
    "            catch (System.Exception) { _gyroEnabled = false; }\n"
    "            _gyroRateEma = 0f;\n"
    "        }\n",
    "cam gyro update")
write(p, s)

# ── 2) UIManager.cs: pulsante GIRO + gestione visuale e persistenza ──
p = base + "/UI/UIManager.cs"
s = read(p)
s = a(s,
    "        private GameObject jobsButtonGo;\n",
    "        private GameObject jobsButtonGo;\n\n"
    "        // pulsante flottante GIRO (giroscopio): gira la visuale ruotando il\n"
    "        // telefono. Stato acceso/spento persistito in PlayerPrefs, il colore\n"
    "        // riflette lo stato vero della camera.\n"
    "        private Image gyroButtonImage;\n"
    "        private TMP_Text gyroButtonLabel;\n",
    "ui gyro fields")
s = a(s,
    "            MakeText(jumpRt, \"SALTA\", 30f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);\n",
    "            MakeText(jumpRt, \"SALTA\", 30f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);\n\n"
    "            // --- pulsante GIRO (giroscopio): sotto SALTA, stesso stile.\n"
    "            var gyroRt = MakeRect(\"GyroButton\", root, new Vector2(1f, 0f), new Vector2(1f, 0f), Vector2.zero, Vector2.zero);\n"
    "            gyroRt.pivot = new Vector2(0.5f, 0.5f);\n"
    "            gyroRt.anchoredPosition = new Vector2(-110f, 60f);\n"
    "            gyroRt.sizeDelta = new Vector2(110f, 84f);\n"
    "            Image gyroBg = gyroRt.gameObject.AddComponent<Image>();\n"
    "            gyroBg.raycastTarget = true;\n"
    "            gyroButtonImage = gyroBg;\n"
    "            Button gyroBtn = gyroRt.gameObject.AddComponent<Button>();\n"
    "            gyroBtn.targetGraphic = gyroBg;\n"
    "            gyroBtn.onClick.AddListener(OnGyroTogglePressed);\n"
    "            gyroButtonLabel = MakeText(gyroRt, \"GIRO\", 26f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);\n"
    "            gyroButtonLabel.raycastTarget = false;\n"
    "            {\n"
    "                var rig = Game.Instance != null ? Game.Instance.rig : City.Player.CameraRig.Instance;\n"
    "                bool on = rig != null ? rig.GyroEnabled : PlayerPrefs.GetInt(\"gyroCam\", 1) == 1;\n"
    "                DrawGyroButton(on);\n"
    "            }\n",
    "ui gyro button")
s = a(s,
    "        private void OnJobsButtonPressed()\n",
    "        private void OnGyroTogglePressed()\n"
    "        {\n"
    "            Vibration.Vibrate(25);\n"
    "            bool now;\n"
    "            var rig = Game.Instance != null ? Game.Instance.rig : City.Player.CameraRig.Instance;\n"
    "            if (rig != null)\n"
    "            {\n"
    "                now = !rig.GyroEnabled;\n"
    "                rig.SetGyroEnabled(now);\n"
    "                PlayerPrefs.SetInt(\"gyroCam\", now ? 1 : 0);\n"
    "            }\n"
    "            else\n"
    "            {\n"
    "                now = PlayerPrefs.GetInt(\"gyroCam\", 1) != 1;\n"
    "                PlayerPrefs.SetInt(\"gyroCam\", now ? 1 : 0);\n"
    "            }\n"
    "            DrawGyroButton(now);\n"
    "            City.OSM.OsmDiag.Log(\"[Camera][Gyro] toggle -> \" + now);\n"
    "        }\n\n"
    "        private void DrawGyroButton(bool on)\n"
    "        {\n"
    "            if (gyroButtonImage == null) return;\n"
    "            gyroButtonImage.color = on\n"
    "                ? new Color(0.15f, 0.65f, 0.95f, 0.85f)\n"
    "                : new Color(0.5f, 0.5f, 0.5f, 0.6f);\n"
    "            if (gyroButtonLabel != null)\n"
    "                gyroButtonLabel.text = on ? \"GIRO\" : \"GIRO OFF\";\n"
    "        }\n\n"
    "        private void OnJobsButtonPressed()\n",
    "ui gyro handler")
write(p, s)

print("OK patch26 applicata (giroscopio + toggle GIRO)")