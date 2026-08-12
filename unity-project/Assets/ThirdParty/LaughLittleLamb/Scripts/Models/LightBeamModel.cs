using System;
using UnityEngine;

namespace Models
{
	[System.Serializable]
	public class LightBeamModel
	{
		[SerializeField] public int[] Positions;
		[SerializeField] public EntityModel SourceEntity;
		public LightBeamModel(EntityModel sourceEntity,int[] indexes)
		{
			SourceEntity=sourceEntity;
			Positions = indexes;
		}
		public bool Contains(int index) => Array.Exists(Positions,x => x == index);
	}
}