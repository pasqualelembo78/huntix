using Cinemachine;
using UnityEngine;
using Views.Entities;

namespace Controllers
{
	public class CameraController : MonoBehaviour
	{
		[SerializeField] private CinemachineBrain brain;
		[SerializeField] private CinemachineTargetGroup targetGroup;
		[SerializeField] private CinemachineVirtualCamera mainVCam;

		public float sheepWeight = 6;
		public float sheepRadius = 5;
		
		public float enemiesWeight= 3;
		public float enemiesRadius= 1;
		
		public float exitsWeight=3;
		public float exitsRadius=1;
		
		public void Initialize(EntityView[] sheeps, EntityView[] enemies, EntityView[] exits)
		{
			foreach (EntityView view in sheeps)
			{
				targetGroup.AddMember(view.transform, sheepWeight, sheepRadius);
			}
			
			foreach (EntityView view in enemies)
			{
				targetGroup.AddMember(view.transform, enemiesWeight, enemiesRadius);
			}
			
			foreach (EntityView view in exits)
			{
				targetGroup.AddMember(view.transform, exitsWeight, exitsRadius);
			}
			mainVCam.Priority = 1;
		}

		public void Terminate()
		{
			mainVCam.Priority = 0;
		}
	}
}