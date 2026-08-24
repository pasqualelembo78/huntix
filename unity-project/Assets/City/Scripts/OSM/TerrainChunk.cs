using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Terreno piatto per chunk (y=0). L'area NON e' fissa 1000x1000: il passo
    /// della griglia e' in GRADI (CityGrid), quindi la larghezza reale in metri
    /// dipende dalla latitudine (a Foggia una colonna chunk e' larga ~1010 m).
    /// L'area esatta arriva da ChunkBuilder, che proietta gli angoli geografici
    /// del chunk: cosi' i bordi condivisi fra chunk adiacenti combaciano alla
    /// frazione di millimetro e non restano strisce senza collider.
    /// </summary>
    public static class TerrainChunk
    {
        private const float Y_TERRAIN = -0.05f;
        private static Material _grassMat;

        /// <summary>area = rettangolo del chunk in coordinate locali XZ (metri).</summary>
        public static GameObject Create(Transform parent, string name, Rect area)
        {
            EnsureMaterial();
            var go = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer),
                typeof(MeshCollider));
            go.transform.SetParent(parent, false);
            go.transform.localPosition = new Vector3(0f, Y_TERRAIN, 0f);

            var mesh = new Mesh { name = "TerrenoChunk" };
            mesh.vertices = new[]
            {
                new Vector3(area.xMin, 0f, area.yMin),
                new Vector3(area.xMax, 0f, area.yMin),
                new Vector3(area.xMin, 0f, area.yMax),
                new Vector3(area.xMax, 0f, area.yMax),
            };
            mesh.uv = new[] { new Vector2(0, 0), new Vector2(1, 0), new Vector2(0, 1), new Vector2(1, 1) };
            mesh.triangles = new[] { 0, 2, 1, 2, 3, 1 };
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            go.GetComponent<MeshFilter>().sharedMesh = mesh;
            go.GetComponent<MeshRenderer>().sharedMaterial = _grassMat;
            go.GetComponent<MeshCollider>().sharedMesh = mesh;
            return go;
        }

        private static void EnsureMaterial()
        {
            if (_grassMat != null) return;
            _grassMat = SafeMaterial(new Color(0.38f, 0.62f, 0.30f), 0.05f);
        }

        /// <summary>
        /// Material con catena di fallback: se neppure Sprites/Default c'è,
        /// l'ultimo ricorso evita new Material(null) che lancerebbe NRE e
        /// ucciderebbe la costruzione di tutti i chunk.
        /// </summary>
        private static Material SafeMaterial(Color baseColor, float smoothness)
        {
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Sprites/Default");
            if (shader == null) return null;
            var mat = new Material(shader);
            if (mat.HasProperty("_BaseColor"))
                mat.SetColor("_BaseColor", baseColor);
            if (mat.HasProperty("_Color"))
                mat.SetColor("_Color", baseColor);
            if (mat.HasProperty("_Smoothness"))
                mat.SetFloat("_Smoothness", smoothness);
            // Mesh procedurali viste quasi solo dall'alto: rendile double-
            // sided cosi' un eventuale winding errato non le rende invisibili.
            if (mat.HasProperty("_Cull"))
                mat.SetFloat("_Cull", 0f);
            return mat;
        }

        public static Material RoadMaterial()
        {
            var mat = SafeMaterial(new Color(0.27f, 0.27f, 0.30f), 0.15f);
            if (mat == null) mat = new Material(Shader.Find("Legacy Shaders/Diffuse"));
            return mat;
        }

        public static Material ParkMaterial()
        {
            return SafeMaterial(new Color(0.33f, 0.58f, 0.28f), 0f);
        }

        public static Material SidewalkMaterial()
        {
            var mat = SafeMaterial(new Color(0.58f, 0.58f, 0.56f), 0.05f);
            if (mat == null) mat = new Material(Shader.Find("Legacy Shaders/Diffuse"));
            return mat;
        }
    }
}
