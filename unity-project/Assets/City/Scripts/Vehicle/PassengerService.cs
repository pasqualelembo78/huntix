using System.Collections.Generic;
using UnityEngine;
using City.UI;
using City.World;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Autostop: genera un passante sul ciglio della strada che, se il
    /// giocatore si ferma vicino, sale in macchina con destinazione
    /// casuale (un POI qualsiasi). Alla consegna ricompensa 10 EUR.
    /// </summary>
    public class PassengerService : MonoBehaviour
    {
        public static PassengerService Instance { get; private set; }

        public static PassengerService Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("PassengerService");
            DontDestroyOnLoad(go);
            return go.AddComponent<PassengerService>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        // stato
        private GameObject ped;
        private VehicleController hostCar;
        private VehiclePoiRegistry.PoiInfo dest;
        private float nextCheck;
        private float dropBonus = 10f;
        private bool pickedUp;
        private float tripTimer;
        private float cooldownUntil;
        private float offerUntil;
        private const float MaxTrip = 300f;
        private const float ArriveDist = 24f;
        private const float PickDist = 5f;
        private const float SpawnInterval = 6f;

        private void Update()
        {
            if (Game.Instance == null) return;
            if (Game.Instance.CurrentVehicle == null) return;
            if (OfferDialog.Instance == null) return;
            var car = Game.Instance.CurrentVehicle;
            float now = Time.time;

            // passeggero a bordo: naviga verso la destinazione / timeout
            if (pickedUp && hostCar != null)
            {
                if (dest != null)
                    NavigationState.Set(dest.name, dest.kind,
                        dest.lat, dest.lng);
                tripTimer += Time.deltaTime;
                var wp = WorldOrigin.ToWorld(dest.lat, dest.lng);
                wp.y = 0f;
                if (HorizontalDist(hostCar.transform.position, wp)
                    < ArriveDist)
                {
                    Deliver(true);
                    return;
                }
                if (tripTimer > MaxTrip)
                    Deliver(false);
                if (car != hostCar)
                {
                    Toast("Il passeggero resta con l'auto precedente.");
                    pickedUp = false;
                    hostCar = null;
                    dest = null;
                }
                return;
            }

            // hitchhiker in suolo: se il giocatore si ferma vicino offri
            if (ped != null && car != null)
            {
                float d = HorizontalDist(ped.transform.position,
                    car.transform.position);
                if (d < PickDist && car.GetCurrentSpeedKmh() < 3f
                    && now > offerUntil && OfferDialog.Instance != null)
                {
                    offerUntil = now + 7f;
                    OfferDialog.Offer("AUTOSTOP",
                        "Una persona sul ciglio della strada ti chiede"
                        + " un passaggio. Accettare?",
                        () => PickUp(car),
                        () => { });
                }
                if (HorizontalDist(ped.transform.position,
                    car.transform.position) > 40f)
                {
                    Destroy(ped); ped = null;
                    cooldownUntil = now + 15f;
                }
            }

            if (now < nextCheck) return;
            nextCheck = now + SpawnInterval;
            if (ped != null || pickedUp
                || now < cooldownUntil) return;
            if (car == null) return;
            if (car.GetCurrentSpeedKmh() > 2f) return;
            if (car.Damage == VehicleDamage.Fire
                || car.Damage == VehicleDamage.Wrecked) return;
            if (UnityEngine.Random.Range(0f, 1f) > 0.82f) return;
            SpawnHitchhiker(car.transform);
        }

        private void SpawnHitchhiker(Transform car)
        {
            DestroyPed();
            var root = new GameObject("Hitchhiker");
            root.transform.SetParent(null);
            Vector3 side = car.right;
            side.y = 0f;
            if (side.sqrMagnitude < 0.01f)
                side = car.forward;
            side.Normalize();
            root.transform.position = car.transform.position
                + car.transform.forward * 15f
                + side * 2.6f + Vector3.up * 0.05f;
            root.transform.rotation =
                Quaternion.LookRotation(-side, Vector3.up);

            var body = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            Destroy(body.GetComponent<Collider>());
            body.name = "Body";
            body.transform.SetParent(root.transform, false);
            body.transform.localPosition = new Vector3(0f, 0.85f, 0f);
            body.transform.localScale = new Vector3(0.5f, 1.1f, 0.5f);
            body.GetComponent<Renderer>().sharedMaterial =
                SimpleMat(new Color(0.2f, 0.45f, 0.85f));

            var head = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Destroy(head.GetComponent<Collider>());
            head.name = "Head";
            head.transform.SetParent(root.transform, false);
            head.transform.localPosition = new Vector3(0f, 1.72f, 0f);
            head.transform.localScale = Vector3.one * 0.4f;
            head.GetComponent<Renderer>().sharedMaterial =
                SimpleMat(new Color(0.9f, 0.76f, 0.62f));

            var arm = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Destroy(arm.GetComponent<Collider>());
            arm.name = "Arm";
            arm.transform.SetParent(root.transform, false);
            arm.transform.localPosition = new Vector3(0.22f, 1.55f, 0f);
            arm.transform.localRotation = Quaternion.Euler(0f, 0f, 30f);
            arm.transform.localScale = new Vector3(0.1f, 0.45f, 0.1f);
            arm.GetComponent<Renderer>().sharedMaterial =
                SimpleMat(new Color(0.85f, 0.6f, 0.45f));

            ped = root;
        }

        private void PickUp(VehicleController car)
        {
            if (ped == null) return;
            pickedUp = true;
            hostCar = car;
            tripTimer = 0f;
            ped.transform.SetParent(car.transform, false);
            ped.transform.localPosition = new Vector3(1.35f, 0f, 0f);
            ped.transform.localRotation = Quaternion.identity;
            ped.transform.localScale = Vector3.one;

            var list = new List<VehiclePoiRegistry.PoiInfo>(
                VehiclePoiRegistry.All());
            if (list.Count == 0)
            {
                Deliver(false);
                return;
            }
            var rr = new System.Random();
            dest = list[rr.Next(list.Count)];
            Toast("Passeggero a bordo: destinazione "
                + dest.name + ".");
            NavigationState.Set(dest.name, dest.kind,
                dest.lat, dest.lng);
        }

        private void Deliver(bool delivered)
        {
            pickedUp = false;
            if (delivered)
            {
                Wallet.Earn((int)dropBonus);
                Toast("Passeggero consegnato a " + dest.name
                    + "! Ricompensa: +" + dropBonus + ".");
            }
            else
            {
                Toast("Il passeggero si e' stufato e scende a piedi.");
            }
            DestroyPed();
            hostCar = null;
            dest = null;
            cooldownUntil = Time.time + 45f;
        }

        private void DestroyPed()
        {
            if (ped != null) { Destroy(ped); ped = null; }
        }

        private static Material SimpleMat(Color c)
        {
            var shader = Shader.Find("Standard");
            if (shader == null)
                shader = Shader.Find("Universal Render Pipeline/Unlit");
            if (shader == null)
                shader = Shader.Find("Sprites/Default");
            return new Material(shader);
        }

        private static void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }

        private static float HorizontalDist(Vector3 a, Vector3 b)
        {
            a.y = 0f; b.y = 0f;
            return Vector3.Distance(a, b);
        }
    }
}
