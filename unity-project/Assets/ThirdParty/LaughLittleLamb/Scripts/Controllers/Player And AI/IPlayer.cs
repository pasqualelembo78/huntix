using System.Threading;
using Cysharp.Threading.Tasks;

namespace Controllers.AI
{
	public interface IPlayer
	{
		public UniTask TakeTurnAsync(CancellationToken token);
	}
}