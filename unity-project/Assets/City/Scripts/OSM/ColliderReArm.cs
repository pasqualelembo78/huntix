using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Guardia anti-"player che vola" (streaming chunk): quando un
    /// edificio prefab viene istanziato mentre il player e' dentro (o a
    /// ridosso de) la sua impronta, il BoxCollider pieno (layer 8) spingerebbe
    /// il CharacterController VERSO L'ALTO (fuga piu' corta = tetto) e, essendo
    /// il layer 8 escluso dalle sonde di GroundSnapper, il player resterebbe
    /// per sempre appollaiato sopra il tetto senza tornare mai a terra.
    /// Il componente tiene il collider DISATTIVATO finche' il player non esce
    /// dall'impronta allargata del margine, poi lo riattiva e si distrugge
    /// da solo. Si monta SOLO sugli edifici istanziati sotto i piedi del
    /// player (quindi costo quasi nullo).
    /// </summary>
    public class ColliderReArm : MonoBehaviour
    {
        private Collider _collider;
        private Vector3 _worldCenter;   // centro impronta in world space
        // Semiasse conservativo (AABB del quadrato ruotato): copre qualunque
        // rotazione dell'edificio (b.r) senza trigonometria.
        private float _halfDiagPlusMargin;

        public void Setup(Collider col, Vector3 worldCenter, float w, float d)
        {
            _collider = col;
            _worldCenter = worldCenter;
            float hw = w * 0.5f;
            float hd = d * 0.5f;
            const float margin = 1.2f;
            _halfDiagPlusMargin = Mathf.Sqrt(hw * hw + hd * hd) + margin;
        }

        private void Update()
        {
            if (_collider == null) { Destroy(this); return; }
            var player = City.Game.Instance != null
                ? City.Game.Instance.player : null;
            if (player == null) return;

            Vector3 p = player.transform.position;
            float dx = Mathf.Abs(p.x - _worldCenter.x);
            float dz = Mathf.Abs(p.z - _worldCenter.z);
            if (dx <= _halfDiagPlusMargin && dz <= _halfDiagPlusMargin) return; // dentro

            _collider.enabled = true;
            OsmDiag.Log("[Building][Guard] collider riattivato (player uscito " +
                "dall'impronta): " + gameObject.name);
            Destroy(this);
        }
    }
}
