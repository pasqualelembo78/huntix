using UnityEngine;

namespace Controllers.Level
{
	public class TimeController : MonoBehaviour
	{
		[SerializeField] private float normalSpeed = 1;
		[SerializeField] private float fastSpeed = 3;
		public void Normal() => Time.timeScale = normalSpeed;
		public void Fast() => Time.timeScale = fastSpeed;
	}
}
