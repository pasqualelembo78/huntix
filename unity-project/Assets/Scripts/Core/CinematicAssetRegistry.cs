using UnityEngine;

namespace Huntix.Core
{
    // Registry delle texture PBR CC0 usate dalla modalita' cinematografica.
    // Riferimenti diretti (no Resources.Load, che non risolve nel build Android
    // di questo progetto: stesso approccio di CityKitAssetRegistry).
    // Popolato a build-time da CinematicAssetsSetup e referenziato dal GameManager.
    [CreateAssetMenu(fileName = "CinematicAssetRegistry", menuName = "Huntix/CinematicAssetRegistry")]
    public class CinematicAssetRegistry : ScriptableObject
    {
        public Texture2D asphaltDiffuse;
        public Texture2D asphaltNormal;
        public Texture2D asphaltAO;
        public Texture2D asphaltRoughness;
        public Texture2D asphaltWornDiffuse;
        public Texture2D asphaltWornNormal;
        public Texture2D pavementDiffuse;
        public Texture2D pavementNormal;
        public Texture2D concreteDiffuse;
        public Texture2D concreteNormal;
        public Texture2D brickWallDiffuse;
        public Texture2D brickWallNormal;
        public Texture2D grassDiffuse;

        public Texture2D Get(string key)
        {
            switch (key)
            {
                case "asphalt-diffuse": return asphaltDiffuse;
                case "asphalt-normal": return asphaltNormal;
                case "asphalt-ao": return asphaltAO;
                case "asphalt-rough": return asphaltRoughness;
                case "worn-diffuse": return asphaltWornDiffuse;
                case "worn-normal": return asphaltWornNormal;
                case "pavement-diffuse": return pavementDiffuse;
                case "pavement-normal": return pavementNormal;
                case "concrete-diffuse": return concreteDiffuse;
                case "concrete-normal": return concreteNormal;
                case "brick-diffuse": return brickWallDiffuse;
                case "brick-normal": return brickWallNormal;
                case "grass-diffuse": return grassDiffuse;
                default: return null;
            }
        }
    }
}
