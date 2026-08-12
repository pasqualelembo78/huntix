namespace Models
{
	[System.Serializable]
	public class DoorEntityModel : EntityModel
	{
		public readonly Observable<bool> IsOpen = new Observable<bool>(false);
		public DoorEntityModel(int index) : base(index)
		{
		}
	}
}