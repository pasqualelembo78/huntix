using System.Collections.Generic;
using Controllers.Animations;
using Controllers.Commands;
using Controllers.Level;
using Models;
using UnityEngine;
using Views;
using Views.Entities;

namespace Controllers.CommandAnimations
{
	public abstract class CommandAnimationController : MonoBehaviour
	{
		protected Dictionary<EntityModel, EntityView> ModelToView;
		protected CommandsController CommandsController;
		protected AnimationQueueSystem AnimationsController;
		protected TilemapController TilemapController;
		protected TilemapModel TilemapModel;
		public void Initialize(CommandsController commandsController,
		                               Dictionary<EntityModel, EntityView> modelToView,
		                               AnimationQueueSystem animationQueueSystem,
		                               TilemapController tilemapController)
		{
			this.TilemapController = tilemapController;
			this.AnimationsController = animationQueueSystem;
			this.CommandsController = commandsController;
			this.ModelToView = modelToView;
			TilemapModel = tilemapController.Model;
			Initialize();
		}

		public abstract void Terminate();

		protected abstract void Initialize();
	}
	
}