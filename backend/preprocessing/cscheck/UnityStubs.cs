// Stub minimi UnityEngine per verifica sintassi/tipi (MAI includere nel progetto Unity)
using System;
using System.Collections.Generic;
    using System.Collections;

namespace UnityEngine
{
    public struct Vector2 { public float x, y;
        public Vector2(float x, float y) { this.x = x; this.y = y; }
        public float magnitude => Mathf.Sqrt(x * x + y * x);
        public float sqrMagnitude => x * x + y * y;
        public Vector2 normalized => this;
        public static Vector2 zero => new Vector2(0,0);
        public static Vector2 one => new Vector2(1,1);
        public static Vector2 up => new Vector2(0,1);
        public static Vector2 right => new Vector2(1,0);
        public static float Distance(Vector2 a, Vector2 b) => 0f;
        public static float Dot(Vector2 a, Vector2 b) => 0f;
        public static Vector2 Scale(Vector2 a, Vector2 b) => a;
        public static Vector2 operator +(Vector2 a, Vector2 b) => a;
        public static Vector2 operator -(Vector2 a, Vector2 b) => a;
        public static Vector2 operator *(Vector2 a, float d) => a;
        public static Vector2 operator /(Vector2 a, float d) => a;
        public static Vector2 operator -(Vector2 a) => a;
        public static implicit operator Vector3(Vector2 v) => new Vector3(v.x, v.y, 0f);
        public static bool operator ==(Vector2 a, Vector2 b) => true;
        public static bool operator !=(Vector2 a, Vector2 b) => false;
        public override bool Equals(object o) => o is Vector2;
        public override int GetHashCode() => 0; }

    public struct Vector3 { public float x, y, z;
        public Vector3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        public float magnitude => Mathf.Sqrt(x * x + y * y);
        public float sqrMagnitude => 0f;
        public void Normalize() {}
        public static Vector3 zero => new Vector3(0,0,0);
        public static Vector3 one => new Vector3(1,1,1);
        public static Vector3 forward => new Vector3(0,0,1);
        public static Vector3 up => new Vector3(0,1,0);
        public static Vector3 down => new Vector3(0,-1,0);
        public static Vector3 right => new Vector3(1,0,0);
        public Vector3 normalized => this;
        public static float Distance(Vector3 a, Vector3 b) => 0f;
        public static Vector3 Scale(Vector3 a, Vector3 b) => a;
        public static float Dot(Vector3 a, Vector3 b) => 0f;
        public static Vector3 Cross(Vector3 a, Vector3 b) => a;
        public static Vector3 operator +(Vector3 a, Vector3 b) => a;
        public static Vector3 operator -(Vector3 a, Vector3 b) => a;
        public static Vector3 operator -(Vector3 a) => a;
        public static Vector3 operator *(Vector3 a, float d) => a;
        public static Vector3 operator /(Vector3 a, float d) => a;
        public static bool operator ==(Vector3 a, Vector3 b) => true;
        public static bool operator !=(Vector3 a, Vector3 b) => false;
        public override bool Equals(object o) => o is Vector3;
        public override int GetHashCode() => 0;
        public static Vector3 ProjectOnPlane(Vector3 v, Vector3 n) => v;
        public static Vector3 Project(Vector3 v, Vector3 n) => v;
        public static Vector3 SmoothDamp(Vector3 c, Vector3 t, ref Vector3 vel, float s) => c;
        public static Vector3 MoveTowards(Vector3 a, Vector3 b, float d) => a;
        public static Vector3 Lerp(Vector3 a, Vector3 b, float t) => a;
        public static float Angle(Vector3 a, Vector3 b) => 0f;
        public static Vector3 Slerp(Vector3 a, Vector3 b, float t) => a;
        public override string ToString() => "";
        public string ToString(string format) => ""; }

        [Serializable] public struct Vector2Int { public int x, y;
        public Vector2Int(int x, int y) { this.x = x; this.y = y; }
        public override bool Equals(object o) => o is Vector2Int v && v.x == x && v.y == y;
        public override int GetHashCode() => x * 397 ^ y;
        public static bool operator ==(Vector2Int a, Vector2Int b) => a.x == b.x && a.y == b.y;
        public static bool operator !=(Vector2Int a, Vector2Int b) => !(a == b); }

    public struct Rect { public float x, y, width, height;
        public Rect(float x, float y, float w, float h) { this.x = x; this.y = y; width = w; height = h; }
        public float xMin => x; public float yMin => y;
        public float xMax => x + width; public float yMax => y + height;
        public bool Contains(Vector2 p) => true;
        public bool Overlaps(Rect r) => false; }

    public struct Quaternion { public float x, y, z, w;
        public static Quaternion Euler(float x, float y, float z) => new Quaternion();
        public static Quaternion identity => new Quaternion();
        public static Quaternion LookRotation(Vector3 dir) => new Quaternion();
        public static Quaternion LookRotation(Vector3 dir, Vector3 up) => new Quaternion();
        public static Quaternion Slerp(Quaternion a, Quaternion b, float t) => new Quaternion();
        public static Quaternion Lerp(Quaternion a, Quaternion b, float t) => new Quaternion();
        public static Quaternion AngleAxis(float deg, Vector3 axis) => new Quaternion();
        public static Quaternion FromToRotation(Vector3 from, Vector3 to) => new Quaternion();
        public static Quaternion Inverse(Quaternion q) => new Quaternion();
        public static float Angle(Quaternion a, Quaternion b) => 0f;
        public Quaternion normalized => this;
        public Vector3 eulerAngles { get => Vector3.zero; set {} }
        public static Quaternion operator *(Quaternion a, Quaternion b) => a;
        public static Vector3 operator *(Quaternion q, Vector3 v) => v; }

    public struct Color { public float r, g, b, a;
        public Color(float r, float g, float b) { this.r = r; this.g = g; this.b = b; a = 1f; }
        public Color(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a; }
        public static implicit operator Color32(Color c) => new Color32(255,255,255,255);
        public static Color white => new Color(1,1,1); public static Color black => new Color(0,0,0);
        public static Color red => new Color(1,0,0); public static Color green => new Color(0,1,0);
        public static Color blue => new Color(0,0,1); public static Color yellow => new Color(1,1,0);
        public static Color gray => new Color(0.5f,0.5f,0.5f);
        public static Color clear => new Color(0,0,0,0);
        public static Color operator *(Color c, float f) => c;
        public static Color operator +(Color a, Color b) => a;
        public static Color Lerp(Color a, Color b, float t) => a; }

    public enum FontStyle { Normal, Bold, Italic, BoldAndItalic }

    public static class Mathf { public const float Deg2Rad = 0.01745f; public const float Rad2Deg = 57.2958f;
        public const float PI = 3.141593f; public const float Epsilon = 1.19e-7f;
        public static float Cos(float f) => 0f; public static float Abs(float f) => f;
        public static int Abs(int i) => i;
        public static float Max(float a, float b) => a; public static float Min(float a, float b) => a;
        public static int Max(int a, int b) => a; public static int Min(int a, int b) => a;
        public static float Clamp(float v, float a, float b) => v;
        public static int Clamp(int v, int a, int b) => v;
        public static float Sqrt(float f) => f;
        public static float Pow(float f, float p) => f;
        public static float Sign(float f) => f >= 0f ? 1f : -1f;
        public static bool Approximately(float a, float b) => false;
        public static float Sin(float f) => 0f; public static float Lerp(float a, float b, float t) => a;
        public static float LerpAngle(float a, float b, float t) => a;
        public static float Repeat(float t, float len) => t;
        public static float DeltaAngle(float a, float b) => 0f;
        public static float SmoothStep(float a, float b, float t) => a;
        public static float InverseLerp(float a, float b, float t) => a;
        public static int RoundToInt(float f) => 0; public static float Atan2(float y, float x) => 0f;
        public static float MoveTowards(float a, float b, float d) => a;
        public static float Clamp01(float v) => v;
        public static int FloorToInt(float f) => 0; public static int CeilToInt(float f) => 0;
        public static float Acos(float f) => 0f; public static float Atan(float f) => 0f;
        public static float Floor(float f) => f; public static float Ceil(float f) => f; }
    public class HeaderAttribute : Attribute { public HeaderAttribute(string h) {} }

    public struct Color32 { public byte r, g, b, a;
        public Color32(byte r, byte g, byte b, byte a)
        { this.r = r; this.g = g; this.b = b; this.a = a; }
        public static implicit operator Color(Color32 c) =>
            new Color(c.r / 255f, c.g / 255f, c.b / 255f, c.a / 255f);
    }

    public class GameObject : Object {
        public static GameObject Find(string name) => null;
        public static GameObject FindGameObjectWithTag(string tag) => null;
        public static GameObject FindWithTag(string tag) => null;
        public bool activeInHierarchy => true;
        public GameObject(string n, params Type[] comps) {}
        public T GetComponent<T>() where T : Component => default(T);
        public T[] GetComponentsInChildren<T>() => new T[0];
        public static GameObject CreatePrimitive(PrimitiveType t) => new GameObject("p");
        public Transform transform => null; public int layer;
        public T AddComponent<T>() where T : Component => default(T);
        public T[] GetComponentsInChildren<T>(bool includeInactive) => new T[0];
        public T GetComponentInChildren<T>() where T : Component => default(T);
        public bool activeSelf => true;
        public GameObject gameObject => null;
        public void SetActive(bool on) {}
        public T GetComponentInParent<T>() where T : Component => default(T);
        public static T FindObjectOfType<T>() where T : Object => default(T);
        public static implicit operator bool(GameObject g) => !ReferenceEquals(g, null);
        public static bool operator !(GameObject g) => ReferenceEquals(g, null);
        public UnityEngine.SceneManagement.Scene scene => new UnityEngine.SceneManagement.Scene(); }

    public class Component : Object
    {
        public Transform transform => null;
        public GameObject gameObject => null;
        public T GetComponent<T>() where T : Component => default(T);
        public T GetComponentInParent<T>() where T : Component => default(T);
        public T GetComponentInChildren<T>() where T : Component => default(T);
        public T GetComponentInChildren<T>(bool includeInactive) where T : Component => default(T);
        public T[] GetComponentsInChildren<T>(bool includeInactive) => new T[0];
        public bool CompareTag(string t) => false;
    }
    public class Behaviour : Component { public bool enabled; }
    public class Transform : Component
    {
        public Vector3 position { get; set; }
        public Vector3 localPosition { get; set; }
        public Vector3 localScale { get; set; }
        public Quaternion localRotation { get; set; }
        public Vector3 localEulerAngles { get => Vector3.zero; set {} }
        public Quaternion rotation { get; set; }
        public Vector3 eulerAngles { get; set; }
        public Transform parent { get; set; }
        public string name { get; set; }
        public Vector3 forward => Vector3.forward;
        public Vector3 right => Vector3.right;
        public Matrix4x4 worldToLocalMatrix => new Matrix4x4();
        public Vector3 lossyScale => default(Vector3);
        public void SetParent(Transform p, bool worldStays) {}
        public void SetParent(Transform p) {}
        public System.Collections.IEnumerator GetEnumerator() { yield break; }
        public void RotateAround(Vector3 point, Vector3 axis, float angle) {}
        public void Rotate(Vector3 e) {}
        public void Rotate(float x, float y, float z) {}
        public void Rotate(Vector3 axis, float angle) {}
        public void Translate(Vector3 d) {}
        public void LookAt(Transform t) {}
        public Transform Find(string n) => null;
        public bool IsChildOf(Transform p) => false;
        public T GetComponentInParent<T>() where T : Component => default(T);
        public T GetComponentInChildren<T>() where T : Component => default(T);
        public T GetComponent<T>() where T : Component => default(T);
        public Rigidbody attachedRigidbody => null;
    }
    public struct Matrix4x4 { public Vector3 MultiplyPoint3x4(Vector3 p) => p; }
    public class Rigidbody : Component
    {
        public Vector3 position { get; set; }
        public float mass { get; set; }
        public float drag { get; set; }
        public float angularDrag { get; set; }
        public RigidbodyInterpolation interpolation { get; set; }
        public CollisionDetectionMode collisionDetectionMode { get; set; }
        public bool isKinematic { get; set; }
        public bool useGravity { get; set; }
        public Vector3 velocity { get; set; }
        public Vector3 angularVelocity { get; set; }
        public void AddForce(Vector3 f) {}
        public void AddForceAtPosition(Vector3 f, Vector3 p) {}
        public void MovePosition(Vector3 p) {}
    }

    public enum RigidbodyInterpolation { None, Interpolate, Extrapolate }
    public enum CollisionDetectionMode { Discrete, Continuous, ContinuousDynamic }

    public class Object { public string name;
        public string tag { get; set; }
        public int GetInstanceID() => 0;
        public static void Destroy(Object o) {}
        public static void Destroy(Object o, float delay) {}
        public static void DestroyImmediate(Object o) {}
        public static void DontDestroyOnLoad(Object o) {}
        public static T Instantiate<T>(T o) where T : Object => o;
        public static UnityEngine.Object Instantiate(UnityEngine.Object o) => o;
        public static UnityEngine.Object Instantiate(UnityEngine.Object o, Transform parent) => o;
        public static UnityEngine.Object Instantiate(UnityEngine.Object o, Transform parent, bool worldPositionStays) => o;
        public static T Instantiate<T>(T o, Transform parent, bool worldPositionStays) where T : Object => o;
        public static UnityEngine.Object Instantiate(UnityEngine.Object o, Vector3 pos, Quaternion rot) => o;
        public static T Instantiate<T>(T o, Transform parent) where T : Object => o;
        public static T FindObjectOfType<T>() where T : Object => default(T);
        public static T[] FindObjectsOfType<T>() where T : Object => new T[0];
        public static UnityEngine.Object FindObjectOfType(System.Type t) => null;
        public static UnityEngine.Object[] FindObjectsOfType(System.Type t) => new UnityEngine.Object[0]; }

    public static class Random { public static float Range(float a, float b) => a;
        public static int Range(int a, int b) => a;
        public static Quaternion rotation => Quaternion.identity;
        public static Vector3 insideUnitSphere => Vector3.zero;
        public static Vector2 insideUnitCircle => Vector2.zero; }

    public class ScriptableObject : Object
    {
        public static T CreateInstance<T>() where T : ScriptableObject, new() => new T();
    }

    public static class Debug { public static void Log(object m) {} public static void LogWarning(object m) {} public static void LogError(object m) {} public static void LogException(System.Exception e) {} }

    public struct Bounds { public Bounds(Vector3 c, Vector3 s) { center = c; size = s; min = c; extents = s; max = c; }
        public Vector3 center, size, min, max, extents;
        public void Encapsulate(Bounds b) {}
        public bool Contains(Vector3 p) => true;
        public Vector3 ClosestPoint(Vector3 p) => p;
        public float SquaredDistance(Vector3 p) => 0f;
        public void Expand(float d) {} }

    public class Mesh : Object { public int vertexCount => 0;
        public Vector3[] vertices { get; set; }
        public Vector2[] uv { get; set; } public int[] triangles { get; set; }
        public void SetVertices(List<Vector3> v) {} public void SetUVs(int i, List<Vector2> u) {}
        public void SetTriangles(List<int> t, int sub) {}
        public void RecalculateNormals() {} public void RecalculateBounds() {} }

    public class Material : Object { public Material(Shader s) {}
        public Shader shader { get; set; }
        public Color color { get; set; }
        public bool HasProperty(string n) => false;
        public void SetColor(string n, Color c) {} public Color GetColor(string n) => new Color();
        public void SetFloat(string n, float v) {} public float GetFloat(string n) => 0f;
        public void SetInt(string n, int v) {} public int GetInt(string n) => 0;
        public void EnableKeyword(string k) {} public void DisableKeyword(string k) {}
        public void SetTexture(string n, Texture t) {}
        public Texture GetTexture(string n) => null;
        public void SetMainTexture(Texture t) {} }

    public class Shader { public string name => ""; public static Shader Find(string n) => null; }

    public class MeshFilter : Component { public Mesh sharedMesh { get; set; } }
    public class SkinnedMeshRenderer : Renderer {
        public Material[] sharedMaterials { get; set; }
        public Transform rootBone { get; set; }
        public Transform[] bones => new Transform[0]; }
    public class RuntimeAnimatorController : Object { }
    public class Animator : Behaviour { public RuntimeAnimatorController runtimeAnimatorController { get; set; }
        public void SetFloat(string n, float v) {} public void SetBool(string n, bool v) {} public void SetTrigger(string n) {} }

    public enum WrapMode { Default, Once, Loop, PingPong, ClampForever }
    public struct Keyframe { public Keyframe(float time, float value) {} }
    public class AnimationCurve
    {
        public AnimationCurve() {}
        public void AddKey(float time, float value) {}
    }
    public class AnimationClip : Object
    {
        public bool legacy { get; set; }
        public WrapMode wrapMode { get; set; }
        public float length => 0f;
        public bool empty => false;
        public void SetCurve(string relativePath, Type type, string propertyName, AnimationCurve curve) {}
        public void SampleAnimation(GameObject go, float time) {}
    }
    public class AnimationState : Behaviour
    {
        public float speed { get; set; }
        public float time { get; set; }
        public float normalizedTime { get; set; }
        public float weight { get; set; }
        public WrapMode wrapMode { get; set; }
        public AnimationClip clip => null;
        public string name { get; set; }
    }
    public class Animation : Behaviour
    {
        public AnimationClip clip { get; set; }
        public bool playAutomatically { get; set; }
        public WrapMode wrapMode { get; set; }
        public void AddClip(AnimationClip c, string newName) {}
        public void RemoveClip(string clipName) {}
        public bool Play(string animation) => true;
        public bool Play() => true;
        public void Stop() {}
        public void Stop(string animation) {}
        public void CrossFade(string animation, float fadeLength) {}
        public void CrossFadeQueued(string animation, float fadeLength, QueueMode mode) {}
        public bool IsPlaying(string animation) => false;
        public AnimationState this[string name] => null;
    }
    public enum QueueMode { CompleteOthers, PlayNow }
    public enum CameraClearFlags { Skybox, SolidColor, Depth, Nothing }
    public class AudioListener : Behaviour { }
    public class Light : Behaviour { public LightType type { get; set; }
        public float intensity { get; set; } public Color color { get; set; }
        public float range { get; set; }
        public Transform transform => null; }
    public enum LightType { Spot, Directional, Point, Area }
    public class Renderer : Component { public Bounds bounds => default(Bounds);
        public Material material { get; set; }
        public Material sharedMaterial { get; set; }
        public Material[] sharedMaterials { get; set; } }
    public class MeshRenderer : Renderer { }
    public class ParticleSystemRenderer : Renderer {}
    public class Collider : Component { public bool enabled { get; set; }
        public bool isTrigger { get; set; }
        public bool CompareTag(string t) => false; }

    public class RequireComponentAttribute : Attribute { public RequireComponentAttribute(Type t) {} }
    public class CreateAssetMenuAttribute : Attribute
    {
        public string fileName;
        public string menuName;
    }
    public class BoxCollider : Collider { public Vector3 size { get; set; } public Vector3 center { get; set; } }
    public class MeshCollider : Collider { public Mesh sharedMesh { get; set; } }
    public class CapsuleCollider : Collider { public float height { get; set; } public float radius { get; set; } public Vector3 center { get; set; } }
    public class SphereCollider : Collider { public float radius { get; set; } public Vector3 center { get; set; } }
    public class CharacterController : Collider
    {
        public bool isGrounded => true;
        public Vector3 center { get; set; }
        public Vector3 velocity => Vector3.zero;
        public float height { get; set; }
        public float radius { get; set; }
        public float slopeLimit { get; set; }
        public float stepOffset { get; set; }
        public CollisionFlags Move(Vector3 motion) => default(CollisionFlags);
    }
    public enum CollisionFlags { None, Sides, Above, Below }

    public class Camera : Component { public static Camera main => null;
        public float farClipPlane { get; set; }
        public CameraClearFlags clearFlags { get; set; }
        public Color backgroundColor { get; set; }
        public bool orthographic { get; set; }
        public float orthographicSize { get; set; }
        public float nearClipPlane { get; set; }
        public float fieldOfView { get; set; }
        public Rect rect { get; set; }
        public bool enabled { get; set; }
        public Ray ScreenPointToRay(Vector3 p) => new Ray(Vector3.zero, Vector3.forward);
        public Ray ScreenPointToRay(Vector2 p) => new Ray(Vector3.zero, Vector3.forward); }

    public static class Application { public static string persistentDataPath => "/tmp"; public static bool isMobilePlatform => false;
        public static RuntimePlatform platform => RuntimePlatform.WindowsEditor; }
    public enum RuntimePlatform { WindowsEditor, Android, IOS, LinuxEditor, WindowsPlayer }
    public enum QueryTriggerInteraction { UseGlobal, Ignore, Collide }

    public static class Physics { public static bool autoSyncTransforms { get; set; }
        public static bool Raycast(Vector3 origin, Vector3 dir, out RaycastHit hit, float maxDist) { hit = default(RaycastHit); return false; }
        public static bool Raycast(Vector3 origin, Vector3 dir, out RaycastHit hit) { hit = default(RaycastHit); return false; }
        public static bool Raycast(Vector3 origin, Vector3 dir, out RaycastHit hit, float maxDist, int mask) { hit = default(RaycastHit); return false; }
        public static bool Raycast(Vector3 origin, Vector3 dir, out RaycastHit hit, float maxDist, int mask, QueryTriggerInteraction q) { hit = default(RaycastHit); return false; }
        public static bool Raycast(Ray ray, out RaycastHit hit, float maxDist) { hit = default(RaycastHit); return false; }
        public static bool Raycast(Ray ray, out RaycastHit hit, float maxDist, int mask) { hit = default(RaycastHit); return false; }
        public static bool Raycast(Ray ray, out RaycastHit hit, float maxDist, int mask, QueryTriggerInteraction q) { hit = default(RaycastHit); return false; }
        public static int OverlapSphereNonAlloc(Vector3 center, float radius, Collider[] results) => 0;
        public static int OverlapSphereNonAlloc(Vector3 center, float radius, Collider[] results, int mask, QueryTriggerInteraction q) => 0; }

    public struct RaycastHit { public Vector3 point; public Vector3 normal; public Collider collider;
        public Transform transform => null; public float distance => 0f; }
    public class Collision
    {
        public Collider collider => null;
        public Vector3 relativeVelocity => Vector3.zero;
        public ContactPoint[] contacts => new ContactPoint[0];
        public GameObject gameObject => null;
        public float impulse => 0f;
    }
    public struct ContactPoint { public Vector3 point; public Vector3 normal; }
    public struct Ray { public Ray(Vector3 o, Vector3 d) { origin = o; direction = d; }
        public Vector3 origin, direction; }

    public enum TouchPhase { Began, Moved, Stationary, Ended, Canceled }
    public struct Touch { public TouchPhase phase; public Vector2 position;
        public Vector2 deltaPosition; public Vector2 rawPosition; public int fingerId; }

    // schermo (mappa espansa)
    public static class Screen
    {
        public static int width => 1080;
        public static int height => 1920;
    }

    public static class Input
    {
        public static bool touchSupported => false;
        public static int touchCount => 0;
        public static Touch GetTouch(int i) => default(Touch);
        public static Vector2 mousePosition => Vector2.zero;
        public static bool GetMouseButtonDown(int b) => false;
        public static bool GetMouseButton(int b) => false;
        public static bool GetMouseButtonUp(int b) => false;
        public static float GetAxis(string a) => 0f;
        public static bool GetKey(KeyCode k) => false;
        public static bool GetKeyDown(KeyCode k) => false;
    }
    public enum KeyCode { Space, Escape, Return }

    public class TextMesh : Component
    {
        public string text { get; set; }
        public float characterSize { get; set; }
        public int fontSize { get; set; }
        public Font font { get; set; }
        public Color color { get; set; }
        public TextAlignment alignment { get; set; }
        public TextAnchor anchor { get; set; }
    }

    public enum TextAlignment { Left, Center, Right }

    public class TooltipAttribute : Attribute { public TooltipAttribute(string t) {} }
    public class HideInInspector : Attribute { }
    public static class Time { public static float time => 0f; public static float deltaTime => 0f; public static float timeScale { get; set; } public static float unscaledDeltaTime => 0f; public static float unscaledTime => 0f; public static float realtimeSinceStartup => 0f; public static float fixedDeltaTime => 0.02f; }
    public static class Resources { public static T Load<T>(string p) where T : Object => default(T);
        public static T[] LoadAll<T>(string p) where T : Object => new T[0];
        public static Object[] LoadAll(string p, Type t) => new Object[0];
        public static T GetBuiltinResource<T>(string p) where T : Object => default(T); }
    public static class JsonUtility { public static T FromJson<T>(string json) => default(T);
        public static string ToJson(object obj) => null; }

    public static class PlayerPrefs
    {
        public static int GetInt(string k, int def = 0) => def;
        public static void SetInt(string k, int v) {}
        public static string GetString(string k, string def = "") => def;
        public static void SetString(string k, string v) {}
        public static void DeleteKey(string k) {}
        public static void Save() {}
    }

    public class MonoBehaviour : Behaviour
    {
        public Coroutine StartCoroutine(IEnumerator routine) => null;
        public void StopCoroutine(Coroutine c) {}
        public void StopAllCoroutines() {}
        public Coroutine StartCoroutine(string method) => null;
    }
    public class Coroutine {}
    public class WaitForSeconds { public WaitForSeconds(float s) {} }
    public class WaitUntil { public WaitUntil(System.Func<bool> predicate) {} }
    public class WaitForSecondsRealtime { public WaitForSecondsRealtime(float s) {} }
    public enum PrimitiveType { Cube, Sphere, Cylinder, Capsule, Plane, Quad }
}

namespace UnityEngine.Networking
{
    using System;
    public enum UnityWebRequestResult { Success, ConnectionError, ProtocolError, DataProcessingError }
    public class UnityWebRequest : IDisposable
    {
        public enum Result { Success, ConnectionError, ProtocolError, DataProcessingError }
        public int timeout;
        public string error => null;
        public Result result => Result.Success;
        public bool isNetworkError => false;
        public bool isHttpError => false;
        public DownloadHandler downloadHandler { get; set; }
        public UploadHandler uploadHandler { get; set; }
        public static UnityWebRequest Get(string url) => new UnityWebRequest();
        public UnityWebRequest() {}
        public UnityWebRequest(string url, string method) {}
        public void SetRequestHeader(string k, string v) {}
        public UnityWebRequestAsyncOperation SendWebRequest() => null;
        public void Dispose() {}
        public static string EscapeURL(string s) => s;
        public static string UnescapeURL(string s) => s;
    }
    public class DownloadHandler { public string text => null; }
    public class DownloadHandlerBuffer : DownloadHandler { public DownloadHandlerBuffer() {} }
    public class UploadHandler {}
    public class UploadHandlerRaw : UploadHandler { public UploadHandlerRaw(byte[] data) {} }
    public class UnityWebRequestAsyncOperation { }
}
namespace UnityEngine.SceneManagement
{
    using System;
    public struct Scene { public string name => null; public bool isLoaded => false; public bool IsValid() => false; }
    public enum LoadSceneMode { Single, Additive }
    public class SceneManager
    {
        public static event Action<Scene, LoadSceneMode> sceneLoaded;
        public static Scene GetActiveScene() => new Scene();
        public static void LoadScene(string name) {}
        public static void LoadScene(int index) {}
    }
}

namespace UnityEngine.UI
{
    using System;
    public enum RenderMode { ScreenSpaceOverlay, ScreenSpaceCamera, WorldSpace }

    public class Canvas : Behaviour { public RenderMode renderMode { get; set; }
        public int sortingOrder { get; set; } }

    public class CanvasScaler : Behaviour
    {
        public enum ScaleMode { ConstantPixelSize, ScaleWithScreenSize }
        public enum ScreenMatchMode { MatchWidthOrHeight, Expand, Shrink }
        public ScaleMode uiScaleMode { get; set; }
        public Vector2 referenceResolution { get; set; }
        public float matchWidthOrHeight { get; set; }
        public ScreenMatchMode screenMatchMode { get; set; }
    }

    public class Graphic : Component
    {
        public Color color { get; set; }
        public bool raycastTarget { get; set; }
        public RectTransform rectTransform => new RectTransform();
    }

    public class Text : Graphic
    {
        public Font font { get; set; }
        public int fontSize { get; set; }
        public FontStyle fontStyle { get; set; }
        public TextAnchor alignment { get; set; }
        public string text { get; set; }
    }

    public class RawImage : Graphic { public Texture texture { get; set; } }
    public class Image : Graphic { public Sprite sprite { get; set; }
        public enum Type { Simple, Sliced, Tiled, Filled }
        public Type type { get; set; }
        public enum FillMethod { Horizontal, Vertical, Radial90, Radial180, Radial360 }
        public FillMethod fillMethod { get; set; }
        public float fillAmount { get; set; } }
    public class Sprite : Object {
        public static Sprite Create(Texture2D tex, Rect rect,
            Vector2 pivot, float pixelsPerUnit) => null;
    }

    // ── widget interattivi (pannelli runtime: concessionaria, garage...) ──
    public class RectOffset { public int left, right, top, bottom;
        public RectOffset(int l, int r, int t, int b) { left = l; right = r; top = t; bottom = b; } }

    public struct Navigation
    {
        public enum Mode { None, Horizontal, Vertical, Automatic, Explicit }
        public Mode mode { get; set; }
    }

    public class Button : Component
    {
        public Graphic targetGraphic { get; set; }
        public Navigation navigation { get; set; }
        public UnityEngine.Events.UnityEvent onClick { get; } = new UnityEngine.Events.UnityEvent();
    }

    public class Mask : Component { public bool showMaskGraphic { get; set; } }
    public class GraphicRaycaster : Component {}

    public class CanvasGroup : Component { public float alpha { get; set; } }

    public class InputField : Component
    {
        public Text textComponent { get; set; }
        public Text placeholder { get; set; }
        public string text { get; set; }
        public UnityEngine.Events.UnityEvent<string> onValueChanged { get; } = new UnityEngine.Events.UnityEvent<string>();
        public UnityEngine.Events.UnityEvent<string> onEndEdit { get; } = new UnityEngine.Events.UnityEvent<string>();
        public void ActivateInputField() {}
    }

    public class ScrollRect : Component
    {
        public enum MovementType { Unrestricted, Elastic, Clamped }
        public RectTransform content { get; set; }
        public RectTransform viewport { get; set; }
        public bool vertical { get; set; }
        public bool horizontal { get; set; }
        public MovementType movementType { get; set; }
    }

    public class LayoutGroup : Component
    {
        public bool childControlWidth { get; set; }
        public bool childControlHeight { get; set; }
        public bool childForceExpandWidth { get; set; }
        public bool childForceExpandHeight { get; set; }
        public float spacing { get; set; }
        public RectOffset padding { get; set; }
    }

    public class VerticalLayoutGroup : LayoutGroup {}
    public class HorizontalLayoutGroup : LayoutGroup {}

    public class ContentSizeFitter : Component
    {
        public enum FitMode { Unconstrained, MinSize, PreferredSize }
        public FitMode verticalFit { get; set; }
        public FitMode horizontalFit { get; set; }
    }
}

namespace UnityEngine.Rendering
{
    public enum CullMode { Off }
}

namespace UnityEngine.Events
{
    public class UnityEventBase {}
    public class UnityEvent : UnityEventBase
    {
        public void AddListener(UnityAction call) {}
        public void RemoveListener(UnityAction call) {}
        public void Invoke() {}
    }
    public class UnityEvent<T> : UnityEventBase
    {
        public void AddListener(UnityAction<T> call) {}
        public void RemoveListener(UnityAction<T> call) {}
        public void Invoke(T arg0) {}
    }
    public delegate void UnityAction();
    public delegate void UnityAction<T>(T arg0);
}

namespace UnityEngine
{
    using System;
    public class Texture : Object {}
    public enum TextureFormat { RGBA32, RGB24 }

    public class Texture2D : Texture
    {
        public Texture2D(int w, int h) {}
        public Texture2D(int w, int h, TextureFormat f, bool mip) {}
        public FilterMode filterMode { get; set; }
        public void SetPixels(Color[] pixels) {}
        public void SetPixels32(Color32[] pixels) {}
        public void SetPixel(int x, int y, Color c) {}
        public void Apply(bool updateMipmaps) {}
    }

    public enum FilterMode { Point, Bilinear, Trilinear }

    public class Font : Object { public Material material { get; set; } }

    public enum TextAnchor { UpperLeft, UpperCenter, UpperRight, MiddleLeft, MiddleCenter, MiddleRight, LowerLeft, LowerCenter, LowerRight }

    public class RectTransform : Transform
    {
        public Vector2 anchorMin { get; set; }
        public Vector2 anchorMax { get; set; }
        public Vector2 pivot { get; set; }
        public Vector2 anchoredPosition { get; set; }
        public Vector2 sizeDelta { get; set; }
        public Vector2 offsetMin { get; set; }
        public Vector2 offsetMax { get; set; }
    }

    public static class RectTransformUtility
    {
        public static bool ScreenPointToLocalPointInRectangle(RectTransform rect,
            Vector2 screenPoint, Camera cam, out Vector2 localPoint)
        {
            localPoint = Vector2.zero;
            return false;
        }
    }
}

namespace UnityEngine.EventSystems
{
    using UnityEngine;
    using UnityEngine.UI;
    public class EventSystem : Behaviour
    {
        public static EventSystem current => null;
        public void RaycastAll(PointerEventData ped,
            System.Collections.Generic.List<RaycastResult> r) {}
    }
    public class StandaloneInputModule : Behaviour {}
    public class PointerEventData
    {
        public PointerEventData(EventSystem es) {}
        public int pointerId;
        public Vector2 position;
        public UnityEngine.Camera pressEventCamera;
    }
    public interface IPointerDownHandler
    {
        void OnPointerDown(PointerEventData eventData);
    }
    public interface IInitializePotentialDragHandler
    {
        void OnInitializePotentialDrag(PointerEventData eventData);
    }
    public interface IDragHandler
    {
        void OnDrag(PointerEventData eventData);
    }
    public interface IPointerUpHandler
    {
        void OnPointerUp(PointerEventData eventData);
    }
    public struct RaycastResult
    {
        public GameObject gameObject;
    }
}

namespace TMPro
{
    using UnityEngine;
    public enum TextAlignmentOptions { Left, Right, Center, TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Justified, Flush, Geometry, CenterGeoLine }

    public class TMP_FontAsset : Object {}

    public class TMP_Text : UnityEngine.UI.Graphic
    {
        public string text { get; set; }
        public float fontSize { get; set; }
        public Color color { get; set; }
        public TextAlignmentOptions alignment { get; set; }
        public TMP_FontAsset font { get; set; }
        public bool enableWordWrapping { get; set; }
        public bool raycastTarget { get; set; }
        public float outlineWidth { get; set; }
        public Color outlineColor { get; set; }
        public TextOverflowModes overflowMode { get; set; }
    }

    public enum TextOverflowModes { Overflow, Ellipsis, Mask, ScrollRect, Page, Linked }


    public class TextMeshProUGUI : TMP_Text {}

    public class TextMeshPro : TMP_Text {}

    public static class TMP_Settings
    {
        public static TMP_FontAsset defaultFontAsset => null;
    }
}
