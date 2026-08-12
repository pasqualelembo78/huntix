using System;
using System.Collections.Generic;
using Model;

namespace Models
{
	[System.Serializable]
	public abstract class DataModel : IDisposable
	{
		public abstract int GetPrimaryKey();
		public Type GetSecondaryKey() => GetType();
		public abstract void Dispose();
	}


	[System.Serializable]
	public abstract class DataModel<T> : DataModel where T : DataModel<T>
	{
		private static readonly List<T> instances = new List<T>();
		public static IReadOnlyList<T> Instances => instances;

		public static T Get(int key) => instances[key];
		public override int GetPrimaryKey() => Index;

		public readonly int Index = Instances.Count;

		protected DataModel()
		{
			AddInstance((T)this);
			Database.Add(this);
		}

		private static void AddInstance(T dataModel) => instances.Add(dataModel);

		private static void RemoveInstance(T dataModel)
		{
			int index = dataModel.Index;
			(instances[index], instances[^1]) = (instances[^1], instances[index]);
			instances.RemoveAt(instances.Count-1);
		}

		public override void Dispose()
		{
			Database.Remove(this);
			RemoveInstance((T)this);
		}
	}
}
