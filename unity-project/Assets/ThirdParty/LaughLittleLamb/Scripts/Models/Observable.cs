using System;
using UnityEngine;

namespace Models
{
	[System.Serializable]
	public class Observable<T>
	{
		public event Action<T> OnUpdate;

		[SerializeField] private T value;

		public Observable(T defaultValue = default)
		{
			value = defaultValue;
		}

		public T Value
		{
			get => value;
			set
			{
				if (this.value.Equals(value))
				{
					return;
				}
				this.value = value;
				OnUpdate?.Invoke(this.value);
			}  
		}

		public void Set(T nValue) => Value = nValue;

		public static implicit operator T(Observable<T> source) => source.value;

	}
}