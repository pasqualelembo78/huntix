using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.InputSystem;

public class InputController : MonoBehaviour, InputMap.IGameplayActions
{
	public event Action OnLookUpEvent;
	public event Action OnLookDownEvent;
	public event Action OnLookLeftEvent;
	public event Action OnLookRightEvent;
	public event Action OnMoveRightEvent;
	public event Action OnMoveDownEvent;
	public event Action OnMoveUpEvent;
	public event Action OnMoveLeftEvent;
	public event Action OnWaitEvent;
	public event Action OnMenuEvent;
	public event Action OnUndoEvent;
	public event Action OnDoEvent;
	public event Action OnSkipLevelEvent;
	public event Action OnRestartLevelEvent;
	
	private InputMap inputMap;

	public void Initialize()
	{
		inputMap = new InputMap();
		inputMap.Gameplay.AddCallbacks(this);
		if (gameObject.activeInHierarchy)
		{
			inputMap.Enable();
		}
#if UNITY_ANDROID && !UNITY_EDITOR
		// Huntix: su telefono il gioco è stato progettato per la tastiera (WASD/arrow).
		// Aggiungiamo un overlay di pulsanti touch che pilotano gli stessi eventi.
		var mc = GetComponent<MobileControls>();
		if (mc == null) mc = gameObject.AddComponent<MobileControls>();
		mc.Configure(this);
#endif
	}

	// ── Huntix: azioni touch (overlay MobileControls) ─────────────
	public void PressMoveUp() { OnMoveUpEvent?.Invoke(); }
	public void PressMoveDown() { OnMoveDownEvent?.Invoke(); }
	public void PressMoveLeft() { OnMoveLeftEvent?.Invoke(); }
	public void PressMoveRight() { OnMoveRightEvent?.Invoke(); }
	public void PressLookUp() { OnLookUpEvent?.Invoke(); }
	public void PressLookDown() { OnLookDownEvent?.Invoke(); }
	public void PressLookLeft() { OnLookLeftEvent?.Invoke(); }
	public void PressLookRight() { OnLookRightEvent?.Invoke(); }
	public void PressWait() { OnWaitEvent?.Invoke(); }
	public void PressMenu() { OnMenuEvent?.Invoke(); }
	public void PressUndo() { OnUndoEvent?.Invoke(); }
	public void PressDo() { OnDoEvent?.Invoke(); }
	public void PressSkipLevel() { OnSkipLevelEvent?.Invoke(); }
	public void PressRestart() { OnRestartLevelEvent?.Invoke(); }

	public void Terminate()
	{
		inputMap.Gameplay.RemoveCallbacks(this);
	}

	private void OnEnable()
	{
		inputMap?.Enable();
	}

	private void OnDisable()
	{
		inputMap?.Disable();
	}

	public void OnLookUp(InputAction.CallbackContext context) { if(context.performed) OnLookUpEvent?.Invoke();}
	public void OnLookDown(InputAction.CallbackContext context) { if(context.performed) OnLookDownEvent?.Invoke();}
	public void OnLookLeft(InputAction.CallbackContext context) { if(context.performed) OnLookLeftEvent?.Invoke();}
	public void OnLookRight(InputAction.CallbackContext context) { if(context.performed) OnLookRightEvent?.Invoke();}
	public void OnMoveRight(InputAction.CallbackContext context) { if(context.performed) OnMoveRightEvent?.Invoke();}
	public void OnMoveDown(InputAction.CallbackContext context) { if(context.performed) OnMoveDownEvent?.Invoke();}
	public void OnMoveUp(InputAction.CallbackContext context) { if(context.performed) OnMoveUpEvent?.Invoke();}
	public void OnMoveLeft(InputAction.CallbackContext context) { if(context.performed) OnMoveLeftEvent?.Invoke();}
	public void OnWait(InputAction.CallbackContext context) { if(context.performed) OnWaitEvent?.Invoke();}
	public void OnMenu(InputAction.CallbackContext context) { if(context.performed) OnMenuEvent?.Invoke();}
	public void OnUndo(InputAction.CallbackContext context) { if(context.performed) OnUndoEvent?.Invoke();}
	public void OnDo(InputAction.CallbackContext context) { if(context.performed) OnDoEvent?.Invoke();}
	public void OnSkipLevel(InputAction.CallbackContext context)  { if(context.performed) OnSkipLevelEvent?.Invoke();}
	public void OnRestart(InputAction.CallbackContext context)  { if(context.performed) OnRestartLevelEvent?.Invoke();}
}
