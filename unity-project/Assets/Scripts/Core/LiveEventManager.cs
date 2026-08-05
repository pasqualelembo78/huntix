using UnityEngine;
using System.Collections.Generic;
using Huntix.Bridge;

namespace Huntix.Core
{
    public class LiveEventManager : MonoBehaviour
    {
        public static LiveEventManager Instance { get; private set; }

        [Header("Event Settings")]
        public float eventCheckInterval = 60f;

        private List<LiveEvent> _activeEvents;
        private float _checkTimer;

        [System.Serializable]
        public class LiveEvent
        {
            public string eventId;
            public EventType eventType;
            public string title;
            public string description;
            public long startTime;
            public long endTime;
            public int rewardMVC;
            public string rewardEggRarity;
            public bool isActive;
            public float progress;
            public float maxProgress;
        }

        public enum EventType
        {
            WEEKLY_CHALLENGE,
            SEASONAL_EVENT,
            LIMITED_TIME,
            COMMUNITY_GOAL,
            DAILY_BONUS
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            _activeEvents = new List<LiveEvent>();
            _checkTimer = 0f;
        }

        private void Update()
        {
            _checkTimer += Time.deltaTime;
            if (_checkTimer >= eventCheckInterval)
            {
                _checkTimer = 0f;
                CheckActiveEvents();
            }
        }

        private void CheckActiveEvents()
        {
            long now = System.DateTimeOffset.UtcNow.ToUnixTimeSeconds();

            foreach (var ev in _activeEvents)
            {
                if (now >= ev.startTime && now <= ev.endTime)
                {
                    ev.isActive = true;
                }
                else
                {
                    ev.isActive = false;
                }
            }
        }

        public void AddEvent(LiveEvent ev)
        {
            _activeEvents.Add(ev);
            Debug.Log($"[LiveEventManager] Event added: {ev.title}");
        }

        public List<LiveEvent> GetActiveEvents()
        {
            return _activeEvents.FindAll(e => e.isActive);
        }

        public void UpdateEventProgress(string eventId, float progress)
        {
            var ev = _activeEvents.Find(e => e.eventId == eventId);
            if (ev != null)
            {
                ev.progress = progress;
                Debug.Log($"[LiveEventManager] Event {eventId} progress: {progress}/{ev.maxProgress}");
            }
        }

        public void ClaimEventReward(string eventId)
        {
            var ev = _activeEvents.Find(e => e.eventId == eventId);
            if (ev != null && ev.isActive)
            {
                Debug.Log($"[LiveEventManager] Reward claimed for {eventId}: {ev.rewardMVC} MVC + {ev.rewardEggRarity} egg chance");
                ev.isActive = false;
                UnityBridge.SendMessageToAndroid("EventRewardClaimed", $"{{\"eventId\":\"{eventId}\",\"rewardMVC\":{ev.rewardMVC}}}");
            }
        }
    }
}