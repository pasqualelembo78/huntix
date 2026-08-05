// Helper edit-time: genera texture procedurali (rumore su tint) salvate come
// asset fratelli. Nessun download, nessuna licenza: puro codice C#.
#if UNITY_EDITOR
using System.IO;
using UnityEngine;

namespace Huntix.EditorTools
{
    internal static class ProceduralTextureUtility
    {
        public static Texture2D GenerateNoiseTint(Color baseColor, int seed, int size = 64)
        {
            var tex = new Texture2D(size, size, TextureFormat.RGBA32, false);
            tex.wrapMode = TextureWrapMode.Repeat;
            tex.filterMode = FilterMode.Bilinear;
            var px = new Color[size * size];
            float h, s, v;
            Color.RGBToHSV(baseColor, out h, out s, out v);
            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    float nx = (x + 0.5f) / size;
                    float ny = (y + 0.5f) / size;
                    float noise = HashNoise(nx, ny, seed) * 0.5f + 0.5f;
                    // leggeroalone ombra ai bordi per dare profondità
                    float ax = Mathf.Abs(nx - 0.5f) * 2f;
                    float ay = Mathf.Abs(ny - 0.5f) * 2f;
                    float edge = Mathf.Clamp01(1f - Mathf.Max(ax, ay) * 0.6f);
                    float nv = Mathf.Lerp(v * 0.80f, v, 0.8f + 0.2f * noise) * edge;
                    px[y * size + x] = Color.HSVToRGB(h, s, nv, true) *
                        new Color(1, 1, 1, baseColor.a);
                }
            }
            tex.SetPixels(px);
            tex.Apply();
            return tex;
        }

        public static void WriteTexturePNG(Texture2D tex, string assetPath)
        {
            string fullPath = Path.GetFullPath(assetPath);
            Directory.CreateDirectory(Path.GetDirectoryName(fullPath));
            File.WriteAllBytes(fullPath, tex.EncodeToPNG());
        }

        private static float HashNoise(float x, float y, int seed)
        {
            int X = (int)(x * 259) + seed;
            int Y = (int)(y * 259) + seed;
            uint h = (uint)((X * 3717717932u) ^ (Y * 2538507951u) ^ seed);
            h ^= (h >> 13);
            h *= 0x5bd1e995u;
            h ^= (h >> 15);
            return (float)(h & 0xffff) / (float)0xffff * 2f - 1f;
        }
    }
}
#endif
