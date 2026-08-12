using System.Collections.Generic;
using UnityEngine;
using UnityEngine.AddressableAssets;

namespace Models
{
	[CreateAssetMenu(menuName = "Level List")]
	public class GameSettings : ScriptableObject
	{
		public int quickLoadLevelIndex = -1;
		public List<AssetReferenceGameObject> levelPrefabs;
		public Mode mode;
	}
	
	public enum Mode
	{
		Human,
		Ai
	}
}
