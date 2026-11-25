using UnityEngine;

namespace City.Events
{
    /// <summary>
    /// Audio degli eventi generato una sola volta (nessun file audio scaricato):
    /// folla, zoccoli e fischio. Sorgenti 3D con distanza massima e volume
    /// basso; fuori raggio non viene aggiornato nulla.
    /// </summary>
    public class VenueAudio : MonoBehaviour
    {
        private static AudioClip _crowd;
        private static AudioClip _hooves;
        private static AudioClip _whistle;
        private AudioSource _crowdSource;
        private AudioSource _fxSource;
        private float _hoofTick;

        public void Setup(Transform parent)
        {
            if (_crowd == null) _crowd = MakeNoise("Folla", 1.0f, 0.22f);
            if (_hooves == null) _hooves = MakeHooves();
            if (_whistle == null) _whistle = MakeWhistle();

            _crowdSource = gameObject.AddComponent<AudioSource>();
            _crowdSource.clip = _crowd;
            _crowdSource.loop = true;
            _crowdSource.volume = 0.10f;
            _crowdSource.spatialBlend = 0.85f;
            _crowdSource.minDistance = 8f;
            _crowdSource.maxDistance = 90f;
            _crowdSource.rolloffMode = AudioRolloffMode.Linear;
            _crowdSource.playOnAwake = false;

            _fxSource = gameObject.AddComponent<AudioSource>();
            _fxSource.volume = 0.18f;
            _fxSource.spatialBlend = 0.9f;
            _fxSource.minDistance = 5f;
            _fxSource.maxDistance = 70f;
            _fxSource.playOnAwake = false;
        }

        public void SetCrowd(bool on)
        {
            if (_crowdSource == null) return;
            if (on && !_crowdSource.isPlaying) _crowdSource.Play();
            else if (!on && _crowdSource.isPlaying) _crowdSource.Stop();
        }

        public void Whistle()
        {
            if (_fxSource != null && _whistle != null) _fxSource.PlayOneShot(_whistle);
        }

        /// <summary>Zoccoli a intervalli: un solo suono breve, non un loop per cavallo.</summary>
        public void TickHooves(float dt, bool running, float distance)
        {
            if (!running || distance > 70f) return;
            _hoofTick -= dt;
            if (_hoofTick > 0f) return;
            _hoofTick = 0.42f;
            if (_fxSource != null) _fxSource.PlayOneShot(_hooves, 0.35f);
        }

        private static AudioClip MakeNoise(string name, float seconds, float amount)
        {
            int rate = 22050;
            int n = (int)(rate * seconds);
            var data = new float[n];
            float smooth = 0f;
            var rnd = new System.Random(1337);
            for (int i = 0; i < n; i++)
            {
                smooth = smooth * 0.96f + ((float)rnd.NextDouble() * 2f - 1f) * 0.04f;
                data[i] = smooth * amount;
            }
            var clip = AudioClip.Create(name, n, 1, rate, false);
            clip.SetData(data, 0);
            return clip;
        }

        private static AudioClip MakeHooves()
        {
            int rate = 22050, n = 2400;
            var data = new float[n];
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)n;
                data[i] = Mathf.Sin(t * 90f) * Mathf.Exp(-t * 9f) * 0.7f;
            }
            var clip = AudioClip.Create("Zoccoli", n, 1, rate, false);
            clip.SetData(data, 0);
            return clip;
        }

        private static AudioClip MakeWhistle()
        {
            int rate = 22050, n = 11025;
            var data = new float[n];
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)rate;
                float env = Mathf.Min(1f, t * 30f) * Mathf.Exp(-t * 3.5f);
                data[i] = Mathf.Sin(t * 2f * Mathf.PI * 1900f) * env * 0.5f;
            }
            var clip = AudioClip.Create("Fischio", n, 1, rate, false);
            clip.SetData(data, 0);
            return clip;
        }
    }
}
