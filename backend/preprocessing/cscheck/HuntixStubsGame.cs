// Stub residui: classi reali ANCORA escluse dal check (vedi EXCL in check.sh).
// Game.cs e UIManager.cs ora compilano davvero: i loro stub sono stati tolti.
using UnityEngine;

namespace City.Interior
{
    // stub di Interior/InteractDoor.cs (escluso dal check)
    public class InteractDoor : MonoBehaviour
    {
        public bool IsFocused => false;
        public string label = "";
        public void Interact() {}
    }
}

namespace City.Economy
{
    // stub di EggController.cs (escluso dal check)
    public class EggController : MonoBehaviour
    {
        public enum Rarity { Common, Uncommon, Rare, Legendary }
        public enum EggType { Strada, Parco, Bosco, Albero, Edificio, Terra, Acqua, Aria, Sabbia, Fango, Breccia }
        public Rarity rarity;
        public EggType eggType;
        public int value = 10;
        public bool Captured => false;
        public bool PlayerNearCanRadar => true;
        public void OnCaptured() {}
        public void StartCapture() {}
    }
}

namespace City.UI
{
    // stub di UI/DynamicJoystick.cs (escluso dal check)
    public class DynamicJoystick : MonoBehaviour
    {
        public Vector2 Value => Vector2.zero;
        public void Configure(RectTransform root, RectTransform baseRt, RectTransform handleRt) {}
    }

    // stub di UI/OrbitZone.cs (escluso dal check)
    public class OrbitZone : MonoBehaviour
    {
        public System.Action<float> OnDragDelta;
    }

    // stub di UI/LegalManager.cs (escluso dal check)
    public class LegalManager : MonoBehaviour
    {
        public bool IsVisible => false;
        public void Init(UnityEngine.UI.Canvas parentCanvas, RectTransform parentRoot) {}
        public void Show() {}
        public void Hide() {}
    }

    public class ScreenFader : UnityEngine.MonoBehaviour
    {
        public float duration = 0.4f;
        public UnityEngine.UI.Image image;
        public void FadeToBlack(System.Action done) {}
        public void FadeFromBlack(System.Action done) {}
    }
}

namespace UnityEngine.InputSystem.UI
{
    // stub del pacchetto Input System usato da UIManager
    public class InputSystemUIInputModule : MonoBehaviour
    {
        public void AssignDefaultActions() {}
    }
}
