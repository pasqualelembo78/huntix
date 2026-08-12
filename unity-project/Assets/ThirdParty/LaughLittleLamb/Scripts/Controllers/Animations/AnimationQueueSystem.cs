using System.Collections;
using System.Collections.Generic;
using System.Text;
using UnityEngine;

namespace Controllers.Animations
{
	public class AnimationQueueSystem : MonoBehaviour
	{
		public bool IsPlaying => activeAnimations.Count > 0 && mode != Mode.None;

		//cada instancia de animacion tiene la lista de "participantes" y asi podemos definir que animaciones tiene
		//depedencias entry si y postergar su ejecucion
		//Agregar modos de reproduccion. Lineal o Concurrente

		private readonly List<AnimationInstance> activeAnimations = new List<AnimationInstance>();
		private readonly Dictionary<AnimationInstance, List<AnimationInstance>> dependencies = new Dictionary<AnimationInstance, List<AnimationInstance>>();
		private readonly Dictionary<AnimationInstance, List<AnimationInstance>> dependentAnimations = new Dictionary<AnimationInstance, List<AnimationInstance>>();
		public bool IsPaused { get; set; }

		public Mode mode;
		public enum Mode { None, Sequential, Parallel }
		
		public AnimationInstance Play(IEnumerator animationRoutine, params object[] participants)
		{
			var animationInstance = new AnimationInstance(animationRoutine,participants);
			if (mode == Mode.None)
			{
				return animationInstance;
			}
			AddActiveAnimation(animationInstance);
			StartCoroutine(PlayAndPauseAnimation(animationInstance));
			return animationInstance;
		}

		private void AddActiveAnimation(AnimationInstance animationInstance)
		{
			List<AnimationInstance> myDependencies = new List<AnimationInstance>();
			foreach (AnimationInstance activeAnimation in activeAnimations)
			{
				if (activeAnimation.ContainsAny(animationInstance.Participants))
				{
					myDependencies.Add(activeAnimation);
					var dependentList = dependentAnimations[activeAnimation];
					dependentList.Add(animationInstance);
				}
			}
			dependencies.Add(animationInstance, myDependencies);
			dependentAnimations.Add(animationInstance, new List<AnimationInstance>());
			activeAnimations.Add(animationInstance);
		}

		private void RemoveActiveAnimation(AnimationInstance animationInstance)
		{
			activeAnimations.Remove(animationInstance);
			var currentlyDependentAnimations = dependentAnimations[animationInstance];
			foreach (var dependentAnimation in currentlyDependentAnimations)
			{
				dependencies[dependentAnimation].Remove(animationInstance);
			}
			dependentAnimations.Remove(animationInstance);
			dependencies.Remove(animationInstance);
		}

		private IEnumerator PlayAndPauseAnimation(AnimationInstance animationInstance)
		{
			while (animationInstance.IsPaused || IsPaused)
			{
				yield return null;
			}

			while (mode == Mode.Sequential && activeAnimations[0] != animationInstance)
			{
				yield return null;
			}

			while (mode == Mode.Parallel && HasDependencies(animationInstance))
			{
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.Append($"{animationInstance.Id} has dependencies:");
				foreach (var otherAnimationInstance in dependencies[animationInstance])
				{
					stringBuilder.Append($"{otherAnimationInstance.Id} ");
				}
				yield return null;
			}
			
			while (animationInstance.Animation.MoveNext())
			{
				yield return animationInstance.Animation.Current;
				while (animationInstance.IsPaused || IsPaused)
				{
					yield return null;
				}
			}
			RemoveActiveAnimation(animationInstance);
		}

		private bool HasDependentAnimations(AnimationInstance animationInstance) => dependentAnimations[animationInstance].Count > 0;
		
		private bool HasDependencies(AnimationInstance animationInstance) => dependencies[animationInstance].Count > 0;
	}

	public class AnimationInstance
	{
		private static int count;
		public readonly int Id = count++;
		public IEnumerator Animation;
		public object[] Participants;
		public bool IsPaused;

		public AnimationInstance(IEnumerator animationRoutine, object[] participants)
		{
			Animation = animationRoutine;
			Participants = participants;
		}

		public bool ContainsAny(object[] otherParticipants)
		{
			foreach (object otherParticipant in otherParticipants)
			{
				foreach (object myParticipant in Participants)
				{
					if (otherParticipant == myParticipant)
					{
						return true;
					}
				}
			}
			return false;
		}
	}
}