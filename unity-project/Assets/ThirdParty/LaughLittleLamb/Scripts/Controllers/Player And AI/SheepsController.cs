using System;
using System.Collections.Generic;
using System.Threading;
using Controllers.AI;
using Controllers.CommandAnimations;
using Controllers.Commands;
using Controllers.Entities;
using Cysharp.Threading.Tasks;
using Models;
using UnityEngine;

namespace Controllers.Player
{
	internal class SheepsController : MonoBehaviour, IPlayer
	{
		private EntitiesController entitiesController;

		private UniTaskCompletionSource turnCompleteSource;
		private CommandsController commandsController;
		private AnimationsController animationsController;
		private InputController inputController;
		private Action backToMenuCallback;
		private Action skipLevelCallback;
		private Action restartLevelCallback;

		public void Initialize(EntitiesController entitiesController,
		                       CommandsController commandsController,
		                       AnimationsController animationsController,
		                       InputController inputController,
		                       Action showMenuCallback,
		                       Action skipLevelCallback,
		                       Action restartLevelCallback)
		{
			this.restartLevelCallback = restartLevelCallback;
			this.skipLevelCallback = skipLevelCallback;
			backToMenuCallback = showMenuCallback;
			this.inputController = inputController;
			this.animationsController = animationsController;
			this.commandsController = commandsController;
			this.entitiesController = entitiesController;
		}

		public void Terminate() { }

		private void RegisterInputs()
		{
			inputController.OnMoveUpEvent += OnMoveUp;
			inputController.OnMoveDownEvent += OnMoveDown;
			inputController.OnMoveLeftEvent += OnMoveLeft;
			inputController.OnMoveRightEvent += OnMoveRight;
			
			inputController.OnLookUpEvent += OnLookUp;
			inputController.OnLookDownEvent += OnLookDown;
			inputController.OnLookLeftEvent += OnLookLeft;
			inputController.OnLookRightEvent += OnLookRight;
			
			inputController.OnUndoEvent += UndoTurn;
			inputController.OnDoEvent += RedoTurn;
			
			inputController.OnWaitEvent += MakeEverySheepWait;
			inputController.OnMenuEvent += BackToMenu;
			
			inputController.OnSkipLevelEvent += SkipLevel;
			
			inputController.OnRestartLevelEvent += RestartLevel;
		}

		private void UnregisterInputs()
		{
			inputController.OnMoveUpEvent -= OnMoveUp;
			inputController.OnMoveDownEvent -= OnMoveDown;
			inputController.OnMoveLeftEvent -= OnMoveLeft;
			inputController.OnMoveRightEvent -= OnMoveRight;
			
			inputController.OnLookUpEvent -= OnLookUp;
			inputController.OnLookDownEvent -= OnLookDown;
			inputController.OnLookLeftEvent -= OnLookLeft;
			inputController.OnLookRightEvent -= OnLookRight;
			
			inputController.OnUndoEvent -= UndoTurn;
			inputController.OnDoEvent -= RedoTurn;
			
			inputController.OnWaitEvent -= MakeEverySheepWait;
			inputController.OnMenuEvent -= BackToMenu;;
			
			inputController.OnSkipLevelEvent -= SkipLevel;

			inputController.OnRestartLevelEvent -= RestartLevel;
		}

		private void RestartLevel() => restartLevelCallback?.Invoke();

		private void SkipLevel() => skipLevelCallback?.Invoke();

		private void BackToMenu() => backToMenuCallback?.Invoke();

		private void OnLookUp() => OnLook(Orientation.Up);
		private void OnLookDown() => OnLook(Orientation.Down);
		private void OnLookLeft() => OnLook(Orientation.Left);
		private void OnLookRight() => OnLook(Orientation.Right);

		private void OnLook(Orientation orientation)
		{
			UnregisterInputs();
			entitiesController.LookTogether(entitiesController.SheepEntityModels, orientation);
			turnCompleteSource.TrySetResult();
		}

		private void OnMoveUp() => MoveEverySheep(Orientation.Up);  
		private void OnMoveDown() => MoveEverySheep(Orientation.Down);  
		private void OnMoveLeft() => MoveEverySheep(Orientation.Left);  
		private void OnMoveRight() => MoveEverySheep(Orientation.Right);

		private Stack<SheepTurnStart> turnStarts = new Stack<SheepTurnStart>();
		[SerializeField] private float rewindSpeed = 3;

		private void MoveEverySheep(Orientation orientation)
		{
			//Debug.Log($">>>>>>>>>>>>>{orientation}");
			UnregisterInputs();
			entitiesController.MoveTogether(entitiesController.SheepEntityModels, orientation);
			turnCompleteSource.TrySetResult();
		}

		private void MakeEverySheepWait()
		{
			UnregisterInputs();
			entitiesController.Wait(entitiesController.SheepEntityModels);
			turnCompleteSource.TrySetResult();
		}

		public async UniTask TakeTurnAsync(CancellationToken token)
		{
			commandsController.Do(new SheepTurnStart());
			RegisterInputs();
            
			gameObject.SetActive(true);
			turnCompleteSource = new UniTaskCompletionSource();
			await turnCompleteSource.Task;
            gameObject.SetActive(false);
			
            UnregisterInputs();

            commandsController.Do(new SheepTurnEnd());
		}

		public async void UndoTurn()
		{
			if (animationsController.IsPlaying)
			{
				return;
			}
			//Debug.Log("Undo");
			if (commandsController.HistoryStack.Count > 1 && commandsController.HistoryStack.Peek() is SheepTurnStart)
			{
				commandsController.Undo();
				commandsController.UndoUntil<SheepTurnStart>(false);
			}
			
			Time.timeScale = rewindSpeed;
			while (animationsController.IsPlaying)
			{
				await UniTask.NextFrame();
			}
			Time.timeScale = 1;
		}

		public async void RedoTurn()
		{
			if (animationsController.IsPlaying)
			{
				return;
			}
			
			commandsController.RedoUntil<SheepTurnStart>(true);
			
			Time.timeScale = rewindSpeed;
			while (animationsController.IsPlaying)	
			{
				await UniTask.NextFrame();
			}
			Time.timeScale = 1;
		}
	}
}