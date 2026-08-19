using System;
using System.Collections;
using UnityEngine;
using City.Player;
using City.UI;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Gestisce il ciclo di vita degli interni: entrata, transizioni tra piani, uscita.
    /// Gli interni vengono generati proceduralmente a y=500 (fuori dalla vista della città).
    /// </summary>
    public class InteriorManager : MonoBehaviour
    {
        public static InteriorManager Instance;

        private const float INTERIOR_Y = 500f;
        private const float INTERIOR_SCALE = 4f;

        private GameObject interiorRoot;
        private InteriorPlayer interiorPlayer;
        private InteriorGenerator generator;

        private Vector3 exitWorldPos;
        private Quaternion exitWorldRot;

        private int currentFloor;
        private int totalFloors;

        public bool IsInside { get; private set; }

        private void Awake()
        {
            Instance = this;
            generator = gameObject.AddComponent<InteriorGenerator>();
        }

        private void Log(string msg)
        {
            Debug.Log("[InteriorManager] " + msg);
            UnityBridge.LogToAndroid("InteriorManager", msg);
        }

        public void EnterInterior(string buildingType, string buildingName,
            float width, float depth, float height, int floors,
            Vector3 worldPos, Quaternion worldRot, Shop shop)
        {
            if (IsInside) { Log("EnterInterior: already inside, skip"); return; }
            Log("EnterInterior: " + buildingName + " (" + buildingType + ")");

            exitWorldPos = worldPos;
            exitWorldRot = worldRot;
            totalFloors = Mathf.Clamp(floors, 1, 5);
            currentFloor = 0;

            if (Game.Instance.fader != null)
            {
                Game.Instance.fader.gameObject.SetActive(true);
                Game.Instance.fader.FadeToBlack(() =>
                {
                    DoEnter(buildingType, buildingName, width, depth, height, shop);
                    Game.Instance.fader.FadeFromBlack(() =>
                    {
                        Game.Instance.fader.gameObject.SetActive(false);
                    });
                });
            }
            else
            {
                DoEnter(buildingType, buildingName, width, depth, height, shop);
            }
        }

        private void DoEnter(string buildingType, string buildingName,
            float width, float depth, float height, Shop shop)
        {
            IsInside = true;

            // Disabilita il player della città
            if (Game.Instance.player != null)
            {
                CharacterController cc = Game.Instance.player.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = false;
                Game.Instance.player.gameObject.SetActive(false);
            }
            if (Game.Instance.rig != null)
                Game.Instance.rig.enabled = false;

            // Genera l'interno
            interiorRoot = new GameObject("Interior_" + buildingName);
            interiorRoot.transform.position = new Vector3(0f, INTERIOR_Y, 0f);

            generator.BuildInterior(interiorRoot.transform, buildingType,
                width, depth, height, totalFloors, shop);

            // Scala l'intero interno: esterno piccolo, interno grande (TARDIS effect)
            interiorRoot.transform.localScale = Vector3.one * INTERIOR_SCALE;

            // Crea il player interno
            CreateInteriorPlayer();

            // Scala il player per matchare l'interno ingrandito
            if (interiorPlayer != null)
            {
                interiorPlayer.transform.localScale = Vector3.one * INTERIOR_SCALE;
                interiorPlayer.moveSpeed *= INTERIOR_SCALE;

                // Regola la camera: con player scalato, la posizione locale va divisa
                // per mantenere la testa alla giusta altezza relativa
                if (interiorPlayer.camera != null)
                {
                    interiorPlayer.camera.transform.localPosition =
                        new Vector3(0f, 1.6f / INTERIOR_SCALE, 0f);
                    interiorPlayer.camera.farClipPlane *= INTERIOR_SCALE;
                }
            }

            // Posiziona il player all'ingresso del piano terra
            float spawnX = 0f;
            float spawnZ = -depth * 0.25f * INTERIOR_SCALE;
            if (interiorPlayer != null)
            {
                interiorPlayer.transform.position = new Vector3(spawnX, INTERIOR_Y + 0.1f * INTERIOR_SCALE, spawnZ);
                interiorPlayer.transform.rotation = Quaternion.identity;
            }

            Log("Entrato in: " + buildingName + " (" + buildingType + ", " + totalFloors + " piani, scale=" + INTERIOR_SCALE + "x)");
        }

        public void ExitInterior()
        {
            if (!IsInside) return;

            if (Game.Instance.fader != null)
            {
                Game.Instance.fader.gameObject.SetActive(true);
                Game.Instance.fader.FadeToBlack(() =>
                {
                    DoExit();
                    Game.Instance.fader.FadeFromBlack(() =>
                    {
                        Game.Instance.fader.gameObject.SetActive(false);
                    });
                });
            }
            else
            {
                DoExit();
            }
        }

        private void DoExit()
        {
            IsInside = false;

            // Distruggi l'interno
            if (interiorRoot != null)
            {
                Destroy(interiorRoot);
                interiorRoot = null;
            }

            // Distruggi il player interno
            if (interiorPlayer != null)
            {
                Destroy(interiorPlayer.gameObject);
                interiorPlayer = null;
            }

            // Ripristina il player della città
            if (Game.Instance.player != null)
            {
                Game.Instance.player.gameObject.SetActive(true);
                CharacterController cc = Game.Instance.player.GetComponent<CharacterController>();
                if (cc != null)
                {
                    cc.enabled = false;
                    Game.Instance.player.transform.position = exitWorldPos + Vector3.up * 0.1f;
                    Game.Instance.player.transform.rotation = exitWorldRot;
                    cc.enabled = true;
                }
                Game.Instance.player.Stop();
            }

            if (Game.Instance.rig != null)
            {
                Game.Instance.rig.enabled = true;
                Game.Instance.rig.SetYaw(exitWorldRot);
            }

            Log("Uscito dall'interno");
        }

        // ── Cambio piano ────────────────────────────────────────────

        public void ChangeFloor(int direction)
        {
            int targetFloor = currentFloor + direction;
            if (targetFloor < 0 || targetFloor >= totalFloors) return;

            if (Game.Instance.fader != null)
            {
                Game.Instance.fader.gameObject.SetActive(true);
                Game.Instance.fader.FadeToBlack(() =>
                {
                    DoChangeFloor(targetFloor);
                    Game.Instance.fader.FadeFromBlack(() =>
                    {
                        Game.Instance.fader.gameObject.SetActive(false);
                    });
                });
            }
            else
            {
                DoChangeFloor(targetFloor);
            }
        }

        private void DoChangeFloor(int targetFloor)
        {
            // Disattiva tutti i piani
            for (int i = 0; i < totalFloors; i++)
            {
                Transform floor = interiorRoot.transform.Find("Floor_" + i);
                if (floor != null) floor.gameObject.SetActive(i == targetFloor);
            }

            currentFloor = targetFloor;

            // Posiziona il player sulla scala del piano target
            if (interiorPlayer != null && generator != null)
            {
                Vector3 stairPos = generator.GetStairPosition(interiorRoot.transform, targetFloor);
                interiorPlayer.transform.position = stairPos;
            }

            Log("Piano: " + currentFloor);
        }

        public int GetCurrentFloor() { return currentFloor; }
        public int GetTotalFloors() { return totalFloors; }

        // ── Player interno ──────────────────────────────────────────

        private void CreateInteriorPlayer()
        {
            var go = new GameObject("InteriorPlayer");
            go.tag = "Player";
            go.layer = go.layer;

            // CharacterController
            var cc = go.AddComponent<CharacterController>();
            cc.height = 1.7f;
            cc.radius = 0.3f;
            cc.center = new Vector3(0f, 0.85f, 0f);

            // Camera principale (prima persona)
            var camGo = new GameObject("InteriorCamera");
            camGo.transform.SetParent(go.transform, false);
            camGo.transform.localPosition = new Vector3(0f, 1.6f, 0f);
            camGo.transform.localRotation = Quaternion.identity;
            var cam = camGo.AddComponent<Camera>();
            cam.nearClipPlane = 0.05f;
            cam.farClipPlane = 50f;
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.15f, 0.18f, 0.22f);
            cam.fieldOfView = 70f;
            camGo.AddComponent<AudioListener>();

            // Luce direzionale locale per illuminare l'interno
            var lightGo = new GameObject("InteriorLight");
            lightGo.transform.SetParent(go.transform, false);
            lightGo.transform.localPosition = new Vector3(0f, 3f, 0f);
            lightGo.transform.localRotation = Quaternion.Euler(50f, -30f, 0f);
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Directional;
            light.intensity = 1.2f;
            light.color = new Color(1f, 0.97f, 0.9f);

            interiorPlayer = go.AddComponent<InteriorPlayer>();
            interiorPlayer.camera = cam;
        }

        // ── Input routing ───────────────────────────────────────────

        public void OnMoveInput(Vector2 input)
        {
            if (interiorPlayer != null)
                interiorPlayer.SetMoveInput(input);
        }

        public void OnLookDelta(float dx)
        {
            if (interiorPlayer != null)
                interiorPlayer.OnLookDelta(dx);
        }
    }
}
