using System;
using System.Collections.Generic;
using System.Linq;
using Controllers.CommandAnimations;
using Controllers.Commands;
using Models;
using UnityEngine;
using Views.Entities;

namespace Controllers.Animations
{
	public class LightAnimationController : CommandAnimationController
	{
		[SerializeField] private LightBeamView lightBeamViewPrefab;

		private readonly Queue<LightBeamView> freeLightBeams = new();
		private CommandListener<TurnLightOnCommand> onDoTurnOnCommandListener;
		private CommandListener<TurnLightOnCommand> onUndoTurnOnCommandListener;
		private readonly Dictionary<LightBeamModel, LightBeamView> activeLightBeams = new();
		private CommandListener<TurnLightOffCommand> onDoTurnOffCommandListener;
		private CommandListener<TurnLightOffCommand> onUndoTurnOffCommandListener;

		protected override void Initialize()
		{
			onDoTurnOnCommandListener = TurnLightOnCommand.OnDo.AddListener(OnDoTurnOn);
			onUndoTurnOnCommandListener = TurnLightOnCommand.OnUndo.AddListener(OnUndoTurnOn);
			
			onDoTurnOffCommandListener = TurnLightOffCommand.OnDo.AddListener(OnDoTurnOff);
			onUndoTurnOffCommandListener = TurnLightOffCommand.OnUndo.AddListener(OnUndoTurnOff);
		}

		public override void Terminate()
		{
			TurnLightOnCommand.OnDo.RemoveListener(onDoTurnOnCommandListener);
			TurnLightOnCommand.OnUndo.RemoveListener(onUndoTurnOnCommandListener);
			
			TurnLightOffCommand.OnDo.RemoveListener(onDoTurnOffCommandListener);
			TurnLightOffCommand.OnUndo.RemoveListener(onUndoTurnOffCommandListener);
		}

		private void OnDoTurnOff(TurnLightOffCommand command)
		{
			//Debug.Log("OnDo>TurnOff");
			LightBeamView view = activeLightBeams[command.LightBeam];
			
			List<object> participants = new List<object>();
			participants.Add(view);
			participants.Add(command.SourceEntity);
			participants.Add(command.LightBeam);
			participants.AddRange(command.LightBeam.Positions.Cast<object>());
			
			activeLightBeams.Remove(command.LightBeam);
			AnimationsController.Play(view.TurnOffAnimation(()=> Release(view)),participants.ToArray());
		}

		private void OnUndoTurnOff(TurnLightOffCommand command)
		{
			//Debug.Log("OnUndo<TurnOff");
			LightBeamView view = GetLightBeam();
			view.gameObject.SetActive(true);
			List<object> participants = new List<object>();
			view.name = $"LightBeam from {command.SourceEntity.Index}";
			
			participants.Add(view);
			participants.Add(command.SourceEntity);
			participants.Add(command.LightBeam);
			participants.AddRange(command.LightBeam.Positions.Cast<object>());

			AnimationsController.Play(view.TurnOnAnimation(Array.ConvertAll(command.LightBeam.Positions, x=> TilemapController.GetWorldPosition(x))), participants.ToArray());
			activeLightBeams.Add(command.LightBeam, view);
			
		}

		private void OnDoTurnOn(TurnLightOnCommand command)
		{
			//Debug.Log("OnDo>TurnOn");
			LightBeamView view = GetLightBeam();
			view.gameObject.SetActive(true);
			List<object> participants = new List<object>();
			view.name = $"LightBeam from {command.SourceEntity.Index}";
			
			participants.Add(view);
			participants.Add(command.SourceEntity);
			participants.Add(command.LightBeam);
			participants.AddRange(command.LightBeam.Positions.Cast<object>());

			AnimationsController.Play(view.TurnOnAnimation(Array.ConvertAll(command.LightBeam.Positions, x=> TilemapController.GetWorldPosition(x))), participants.ToArray());
			activeLightBeams.Add(command.LightBeam, view);
		}
		
		private void OnUndoTurnOn(TurnLightOnCommand command)
		{
			//Debug.Log("OnUndo<TurnOn");
			LightBeamView view = activeLightBeams[command.LightBeam];
			
			List<object> participants = new List<object>();
			participants.Add(view);
			participants.Add(command.SourceEntity);
			participants.Add(command.LightBeam);
			participants.AddRange(command.LightBeam.Positions.Cast<object>());
			
			activeLightBeams.Remove(command.LightBeam);
			AnimationsController.Play(view.TurnOffAnimation(()=> Release(view)),participants.ToArray());
		}

		private void Release(LightBeamView view)
		{
			freeLightBeams.Enqueue(view);
			view.gameObject.SetActive(false);
		}

		private LightBeamView GetLightBeam()
		{
			return freeLightBeams.Count == 0 ? Instantiate(lightBeamViewPrefab,transform) : freeLightBeams.Dequeue();
		}
	}
}
