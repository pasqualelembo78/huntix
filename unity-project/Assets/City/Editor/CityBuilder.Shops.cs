using UnityEditor;
using UnityEngine;
using City.World;

namespace City.Editor
{
    public static partial class CityBuilder
    {
        private static int _shopIndex;

        private static void BuildShops(Transform parent)
        {
            Transform shopsRoot = new GameObject("Negozi").transform;
            shopsRoot.SetParent(parent, false);

            // Market: blocco (0,-1) a sud della piazza
            BuildShopAtBlock("Market", 0, -1, parent,
                new ShopItem("Latte", 2), new ShopItem("Acqua", 1), new ShopItem("Pane", 3), new ShopItem("Mele", 1));
            // Panificio: blocco (-1,0) a ovest della piazza
            BuildShopAtBlock("Panificio", -1, 0, parent,
                new ShopItem("Pane al Sesamo", 3), new ShopItem("Cornetto", 2), new ShopItem("Torta", 6));
            // Boutique: blocco (1,0) a est della piazza
            BuildShopAtBlock("Boutique", 1, 0, parent,
                new ShopItem("Maglietta", 15), new ShopItem("Jeans", 25), new ShopItem("Cappellino", 10));
        }

        private static Vector3 BlockCenter(int bi, int bj)
        {
            return new Vector3((bi + 0.5f) * SP, 0f, (bj + 0.5f) * SP);
        }

        private static void BuildShopAtBlock(string name, int bi, int bj, Transform parent, params ShopItem[] items)
        {
            Vector3 center = BlockCenter(bi, bj);
            Vector3 toPlaza = BlockCenter(0, 0) - center;
            toPlaza.y = 0f;
            Quaternion rot = Quaternion.LookRotation(toPlaza.normalized);
            BuildShop(name, center, rot, parent, items);
        }

        private static void BuildShop(string name, Vector3 pos, Quaternion streetDir, Transform parent, params ShopItem[] items)
        {
            int idx = _shopIndex % 3;
            _shopIndex++;

            Transform root = BuildBuildingShell(name, pos, streetDir, parent, ShopColor(idx));

            // vetrine ai lati della porta
            CreateCubeRot(name + " - Vetrina Sx", new Vector3(3.0f, 1.4f, 0.06f),
                root.TransformPoint(new Vector3(-2.65f, 1.6f, -3.61f)), streetDir, root, Window());
            CreateCubeRot(name + " - Vetrina Dx", new Vector3(3.0f, 1.4f, 0.06f),
                root.TransformPoint(new Vector3(2.65f, 1.6f, -3.61f)), streetDir, root, Window());

            // insegna sopra la porta
            Color[] signColors =
            {
                new Color(0.80f, 0.75f, 0.30f),
                new Color(0.65f, 0.45f, 0.25f),
                new Color(0.80f, 0.45f, 0.55f),
            };
            CreateCubeRot(name + " - Insegna", new Vector3(2.4f, 0.5f, 0.35f),
                root.TransformPoint(new Vector3(0f, 3.45f, -3.8f)), streetDir, root, Lit(signColors[idx]));

            // arredamento
            InstLocal(FurnDir + "table.fbx", root, new Vector3(0f, 0f, 1.4f), Quaternion.identity);
            InstLocal(FurnDir + "table.fbx", root, new Vector3(-2.5f, 0f, 1.4f), Quaternion.identity);
            InstLocal(FurnDir + "bookcaseOpen.fbx", root, new Vector3(4.1f, 0f, -1f), Quaternion.Euler(0f, 90f, 0f));
            InstLocal(FurnDir + "bookcaseOpen.fbx", root, new Vector3(-4.1f, 0f, 1f), Quaternion.Euler(0f, -90f, 0f));
            InstLocal(FurnDir + "chair.fbx", root, new Vector3(1.2f, 0f, 0.6f), Quaternion.Euler(0f, 180f, 0f));
            InstLocal(FurnDir + "chair.fbx", root, new Vector3(-1.2f, 0f, 0.6f), Quaternion.Euler(0f, 180f, 0f));
            InstLocal(FurnDir + "plantSmall1.fbx", root, new Vector3(-3.9f, 0f, -2.8f), Quaternion.identity);
            InstLocal(FurnDir + "plantSmall2.fbx", root, new Vector3(3.9f, 0f, -2.8f), Quaternion.identity);

            // commesso dietro il bancone
            GameObject clerk = Inst(CharDir + "Model/characterMedium.fbx",
                root.TransformPoint(new Vector3(0f, 0f, 0.8f)), root.rotation * Quaternion.LookRotation(Vector3.back), root);
            if (clerk != null)
            {
                clerk.name = "Commesso";
                Vector3 p = clerk.transform.position;
                p.y = -GetBounds(clerk).min.y + 0.05f;
                clerk.transform.position = p;
                Animator ca = clerk.GetComponentInChildren<Animator>();
                if (ca != null) ca.enabled = false;
            }

            // negozio + commesso interagibile
            Shop shop = root.gameObject.AddComponent<Shop>();
            shop.shopName = name;
            if (items != null) shop.items.AddRange(items);

            InteractDoor clerkDoor = CreateDoorTrigger("Parla", root.TransformPoint(new Vector3(0f, 1.2f, 0.8f)), new Vector3(3f, 2.8f, 3.4f), "PARLA", root);
            clerkDoor.opensShop = true;
            clerkDoor.shop = shop;
        }

        private static InteractDoor CreateDoorTrigger(string name, Vector3 center, Vector3 size, string label, Transform parent)
        {
            GameObject go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.position = center;
            BoxCollider col = go.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = size;
            InteractDoor door = go.AddComponent<InteractDoor>();
            door.label = label;
            return door;
        }
    }
}
