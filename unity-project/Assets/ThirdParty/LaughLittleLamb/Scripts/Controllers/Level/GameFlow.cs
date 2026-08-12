using System;
using Cysharp.Threading.Tasks;
using Models;
using UnityEngine;
using UnityEngine.AddressableAssets;
using UnityEngine.SceneManagement;
using UnityEngine.Tilemaps;
using Views.Canvases;

namespace Controllers.Level
{
	public class GameFlow : MonoBehaviour
	{
		[SerializeField] private WinCanvasView winCanvasView;
		public static GameFlow Instance { get; set; }
		[SerializeField] private GameSettings settings;
		[SerializeField] private AssetReference levelScene;
		[SerializeField] private AssetReference mainMenu;

		
		private void Awake()
		{
			Instance = this;
			DontDestroyOnLoad(gameObject);
		}

		private async UniTaskVoid Start()
		{
		MainMenu:
			int levelIndex = -1;
			
			#if UNITY_EDITOR
			if (settings.quickLoadLevelIndex != -1)
			{
				levelIndex = settings.quickLoadLevelIndex;
				settings.quickLoadLevelIndex = -1;
			}
			#endif
			MainMenuCanvasView.Result menuResult;
			if (levelIndex > -1)
			{
				menuResult = new MainMenuCanvasView.PlayResult();
			}
			else
			{
				menuResult = await RunMenu();
				levelIndex = 0;
			}

			if (menuResult is MainMenuCanvasView.QuitResult)
			{
				goto Quit;
			}
			
			if (menuResult is MainMenuCanvasView.PlayResult)
			{
			Level:
				LevelController.Result levelResult = await RunLevel(levelIndex);
				
				if (levelResult is LevelController.QuitResult)
				{
					goto MainMenu;
				}

				if (levelResult is LevelController.WinResult)
				{
					levelIndex++;
					if (levelIndex == settings.levelPrefabs.Count)
					{
						switch (await winCanvasView.Run())
						{
							case WinCanvasView.QuitResult:
								goto MainMenu;
							case WinCanvasView.RestartResult:
								levelIndex = 0;
								goto Level;
							default:
								throw new ArgumentOutOfRangeException();
						}		
					}
					goto Level;
				}

				if (levelResult is LevelController.RestartResult)
				{
					goto Level;
				}
			}
			
		Quit:
			Application.Quit();
		}

		private async UniTask<MainMenuCanvasView.Result> RunMenu()
		{
			var sceneHandler = Addressables.LoadSceneAsync(mainMenu, LoadSceneMode.Additive);
			await sceneHandler.Task;
			
			MainMenuCanvasView mainMenuCanvasView = FindFirstObjectByType<MainMenuCanvasView>();
			var result = await mainMenuCanvasView.Run();
			await Addressables.UnloadSceneAsync(sceneHandler).Task;

			return result;
		}

		private async UniTask<LevelController.Result> RunLevel(int levelIndex)
		{
			//Debug.Log($"RunLevel:{levelIndex}");
			var levelSceneHandle = Addressables.LoadSceneAsync(levelScene, LoadSceneMode.Additive);

			var tilemapHandler = settings.levelPrefabs[levelIndex].InstantiateAsync();

			//Debug.Log($"Loading Level");
			await levelSceneHandle.Task;
			//Debug.Log($"Loading Tilemap");
			await tilemapHandler.Task;

			var tilemapGo = tilemapHandler.Result;

			var terrainTilemap = tilemapGo.transform.GetChild(0).GetComponent<Tilemap>();
			var entitiesTilemap = tilemapGo.transform.GetChild(1).GetComponent<Tilemap>();
			
			var levelController = FindFirstObjectByType<LevelController>();
			
			//Debug.Log($"Running Level");
			var result = await levelController.Run(terrainTilemap,entitiesTilemap,settings.mode);

			await Addressables.UnloadSceneAsync(levelSceneHandle).Task;
			Addressables.ReleaseInstance(tilemapHandler);

			return result;
		}
	}
}
