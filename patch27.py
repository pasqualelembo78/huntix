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
p = base + "/Player/CameraRig.cs"
s = read(p)

# 1) campi pitch del giroscopio accanto a quelli yaw
s = a(s,
    "        private bool _gyroEnabled = true;\n        private float _gyroRateEma;\n",
    "        private bool _gyroEnabled = true;\n"
    "        private float _gyroRateEma;\n"
    "        // Pitch (verticale) dal giroscopio: alzi il telefono -> vedi il cielo,\n"
    "        // lo abbassi -> vedi il terreno. Come lo yaw: effetto proporzionale,\n"
    "        // riporti il telefono in linea e la vista torna all'orizzonte.\n"
    "        public const float GyroPitchMax = 70f;\n"
    "        private float _gyroPitchEma;\n"
    "        private float _gyroPitchOffset;\n",
    "cam gyro pitch fields")

# 2) pivot del blocco loro: Update chiama ApplyGyro e il metodo gestisce yaw+pitch
old = ("        private void Update()\n"
       "        {\n"
       "            ApplyGyroYaw();\n"
       "            if (drivingMode) return;\n"
       "            HandlePinchZoom();\n"
       "        }\n\n"
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
       "        }\n")
new = ("        private void Update()\n"
       "        {\n"
       "            ApplyGyro();\n"
       "            if (drivingMode) return;\n"
       "            HandlePinchZoom();\n"
       "        }\n\n"
       "        /// <summary>Rotazione 360° con il giroscopio. Yaw (asse Y del\n"
       "        /// device): giri il telefono a destra/sinistra (come il busto)\n"
       "        /// -> guardi intorno. Pitch (asse X): alzi o abbassi il telefono\n"
       "        /// -> guardi cielo/terreno, anche in diagonale (i due assi si\n"
       "        /// sommano). Effetto proporzionale-posizionale come il drag:\n"
       "        /// nessun salto, si accumula mentre muovi il telefono e resta\n"
       "        /// dov'e' quando lo fermi. Se una delle due rotazioni fosse\n"
       "        /// invertita, cambia il segno della costante corrispondente.</summary>\n"
       "        private void ApplyGyro()\n"
       "        {\n"
       "            if (!_gyroEnabled || !SystemInfo.supportsGyroscope) return;\n"
       "            Vector3 rate;\n"
       "            try { rate = Input.gyro.rotationRateUnbiased; }\n"
       "            catch (System.Exception) { return; }\n"
       "\n"
       "            float rY = Mathf.Abs(rate.y) < GyroDeadzone ? 0f : rate.y;\n"
       "            _gyroRateEma = Mathf.Lerp(_gyroRateEma, rY, GyroSmoothing);\n"
       "            yaw += _gyroRateEma * GyroSensitivity * Time.deltaTime;\n"
       "\n"
       "            float rX = Mathf.Abs(rate.x) < GyroDeadzone ? 0f : rate.x;\n"
       "            _gyroPitchEma = Mathf.Lerp(_gyroPitchEma, rX, GyroSmoothing);\n"
       "            _gyroPitchOffset = Mathf.Clamp(\n"
       "                _gyroPitchOffset + _gyroPitchEma * GyroSensitivity * Time.deltaTime,\n"
       "                -GyroPitchMax, GyroPitchMax);\n"
       "        }\n")
s = a(s, old, new, "cam gyro 360")

# 3) la rotazione della camera aggiunge il pitch del giroscopio alla vista
old = ("            transform.rotation = Quaternion.Euler(Mathf.Lerp(p, 0f, _fpBlend), yaw, 0f);\n")
new = ("            float viewPitch = Mathf.Lerp(p, 0f, _fpBlend)\n"
       "                + (_gyroEnabled ? _gyroPitchOffset : 0f);\n"
       "            transform.rotation = Quaternion.Euler(viewPitch, yaw, 0f);\n")
s = a(s, old, new, "cam gyro pitch rot")

# 4) azzerare pitch anche nello spegnimento/riaccensione del sensore
old = ("        private void SetGyroSensor(bool on)\n"
       "        {\n"
       "            try { Input.gyro.enabled = on; }\n"
       "            catch (System.Exception) { _gyroEnabled = false; }\n"
       "            _gyroRateEma = 0f;\n"
       "        }\n")
new = ("        private void SetGyroSensor(bool on)\n"
       "        {\n"
       "            try { Input.gyro.enabled = on; }\n"
       "            catch (System.Exception) { _gyroEnabled = false; }\n"
       "            _gyroRateEma = 0f;\n"
       "            _gyroPitchEma = 0f;\n"
       "            _gyroPitchOffset = 0f;\n"
       "        }\n")
s = a(s, old, new, "cam gyro sensor reset")

write(p, s)
print("OK patch27 applicata (giroscopio 360°)")