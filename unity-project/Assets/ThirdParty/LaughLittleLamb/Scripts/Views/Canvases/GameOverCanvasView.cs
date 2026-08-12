using System.Threading;
using System.Threading.Tasks;
using Cysharp.Threading.Tasks;
using UnityEngine;
using UnityEngine.UI;

namespace Views.Canvases
{
	public class GameOverCanvasView : MonoBehaviour
	{
		[SerializeField] private Button restartBtn;
		[SerializeField] private Button quitBtn;

		private UniTaskCompletionSource<Result> resultCompletionSource;

		[SerializeField] private float animDuration = .5f; 
		[SerializeField] private AnimationCurve alphaCurve; 
		[SerializeField] private CanvasGroup canvasGroup;
	
		public async UniTask<Result> Run(CancellationToken cancellationToken)
		{
			gameObject.SetActive(true);
			canvasGroup.alpha = 0;
			canvasGroup.interactable = false;
			await ShowAnimationAsync();
			canvasGroup.interactable = true;
			restartBtn.onClick.AddListener(Restart);
			quitBtn.onClick.AddListener(Quit);
			
			resultCompletionSource = new UniTaskCompletionSource<Result>();
			
			(bool hasResult, var result) = await UniTask.WhenAny(
				resultCompletionSource.Task,
				UniTask.WaitUntilCanceled(cancellationToken));
			if (hasResult)
			{
				canvasGroup.interactable = false;
				restartBtn.onClick.RemoveListener(Restart);
				quitBtn.onClick.RemoveListener(Quit);
			}
			return result;
		}

		private async Task ShowAnimationAsync()
		{
			float t = 0;
			do
			{
				t += Time.deltaTime / animDuration;
				canvasGroup.alpha = alphaCurve.Evaluate(t);
				await UniTask.NextFrame();
			} while (t<1);
		}

		private void Restart() => resultCompletionSource?.TrySetResult(new RestartResult());

		private void Quit() => resultCompletionSource?.TrySetResult(new QuitResult());

		public abstract class Result { }
		public class RestartResult: Result { }
		public class QuitResult: Result { }
	
	}
}
