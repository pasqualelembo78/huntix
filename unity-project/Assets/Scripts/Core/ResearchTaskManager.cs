using UnityEngine;
using System.Collections.Generic;
using Huntix.Bridge;

namespace Huntix.Core
{
    public class ResearchTaskManager : MonoBehaviour
    {
        public static ResearchTaskManager Instance { get; private set; }

        [Header("Task Settings")]
        public float checkInterval = 30f;

        private List<ResearchTask> _tasks;
        private float _checkTimer;

        [System.Serializable]
        public class ResearchTask
        {
            public string taskId;
            public string title;
            public string description;
            public TaskType taskType;
            public int targetProgress;
            public int currentProgress;
            public bool isComplete;
            public bool isClaimed;
            public int rewardMVC;
            public string rewardEggRarity;
            public long startTime;
            public long endTime;
        }

        public enum TaskType
        {
            CAPTURE_EGGS,
            VISIT_LOCATION,
            CATCH_RARE_EGG,
            COMPLETE_MINI_GAME,
            DAILY_LOGIN,
            PARTICIPATE_EVENT
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
            _tasks = new List<ResearchTask>();
            _checkTimer = 0f;
        }

        private void Update()
        {
            _checkTimer += Time.deltaTime;
            if (_checkTimer >= checkInterval)
            {
                _checkTimer = 0f;
                CheckTaskProgress();
            }
        }

        public void AddTask(ResearchTask task)
        {
            _tasks.Add(task);
            Debug.Log($"[ResearchTaskManager] Task added: {task.title}");
        }

        public List<ResearchTask> GetActiveTasks()
        {
            return _tasks.FindAll(t => !t.isComplete && !t.isClaimed);
        }

        public List<ResearchTask> GetCompletedTasks()
        {
            return _tasks.FindAll(t => t.isComplete && !t.isClaimed);
        }

        public void UpdateTaskProgress(string taskId, int amount)
        {
            var task = _tasks.Find(t => t.taskId == taskId);
            if (task != null && !task.isComplete)
            {
                task.currentProgress += amount;
                Debug.Log($"[ResearchTaskManager] Task {taskId} progress: {task.currentProgress}/{task.targetProgress}");
            }
        }

        public void MarkTaskComplete(string taskId)
        {
            var task = _tasks.Find(t => t.taskId == taskId);
            if (task != null && !task.isComplete)
            {
                task.isComplete = true;
                Debug.Log($"[ResearchTaskManager] Task complete: {task.title}");
            }
        }

        public void ClaimTaskReward(string taskId)
        {
            var task = _tasks.Find(t => t.taskId == taskId);
            if (task != null && task.isComplete && !task.isClaimed)
            {
                task.isClaimed = true;
                Debug.Log($"[ResearchTaskManager] Reward claimed for {task.title}: {task.rewardMVC} MVC");
                UnityBridge.SendMessageToAndroid("TaskRewardClaimed", $"{{\"taskId\":\"{taskId}\",\"rewardMVC\":{task.rewardMVC},\"rewardEggRarity\":\"{task.rewardEggRarity}\"}}");
            }
        }

        private void CheckTaskProgress()
        {
            foreach (var task in _tasks)
            {
                if (!task.isComplete && !task.isClaimed)
                {
                    if (task.currentProgress >= task.targetProgress)
                    {
                        task.isComplete = true;
                        Debug.Log($"[ResearchTaskManager] Task complete: {task.title}");
                    }
                }
            }
        }
    }
}