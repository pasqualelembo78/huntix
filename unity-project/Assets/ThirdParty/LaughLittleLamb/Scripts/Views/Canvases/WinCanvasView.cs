using Cysharp.Threading.Tasks;
using UnityEngine;
using UnityEngine.UI;

namespace Views.Canvases
{
	public class WinCanvasView : MonoBehaviour
	{
		[SerializeField] private Button restartBtn;
		[SerializeField] private Button quitBtn;

		private UniTaskCompletionSource<Result> resultCompletionSource;

		public async UniTask<Result> Run()
		{
			gameObject.SetActive(true);
			restartBtn.onClick.AddListener(Restart);
			quitBtn.onClick.AddListener(Quit);
			resultCompletionSource = new UniTaskCompletionSource<Result>();
			var result = await resultCompletionSource.Task;
			restartBtn.onClick.RemoveListener(Restart);
			quitBtn.onClick.RemoveListener(Quit);
			gameObject.SetActive(false);
			return result;
		}

		private void Restart() => resultCompletionSource?.TrySetResult(new RestartResult());
		private void Quit() => resultCompletionSource?.TrySetResult(new QuitResult());

		public abstract class Result { }
		public class RestartResult : Result { }
		public class QuitResult : Result { }

	}
}
