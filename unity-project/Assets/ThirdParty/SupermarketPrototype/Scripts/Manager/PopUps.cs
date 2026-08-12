using System;
using UnityEngine;
using Supermarket.Products;
using Cysharp.Threading.Tasks;
using Hieki.Pubsub;

public class PopUps : MonoBehaviour
{
    static PaymentTerminal paymentTerminal;
    static MoneyChanger moneyChanger;
    static SellPanel sellPopup;

    public Transform parent;

    ISubscriber subscriber = new Subscriber();

    private void Awake()
    {
        subscriber.Subscribe<CheckoutDesk.CardPayNotify>(CheckoutDesk.CardPayTopic, CardPay);
        subscriber.Subscribe<CheckoutDesk.CashPayNotify>(CheckoutDesk.CashPayTopic, CashPay);

        subscriber.Subscribe<Furniture.TrySellNotify>(Furniture.trySellTopic, SellFurniture);
    }

    public void CardPay(CheckoutDesk.CardPayNotify notify)
    {
        PaymentTerminal(notify.value, notify.OnCorrect, notify.OnIncorrect).Forget();
    }

    public void CashPay(CheckoutDesk.CashPayNotify notify)
    {
        MoneyChange(notify.value, notify.OnClick, notify.OnReset, notify.OnCorrect, notify.OnIncorrect).Forget();
    }

    public void SellFurniture(Furniture.TrySellNotify notify)
    {
        SellPopup(notify.OnConfirm).Forget();
    }

    public async UniTaskVoid PaymentTerminal(float value, Action OnCorrect, Action OnIncorrect)
    {
        if (paymentTerminal == null)
        {
            paymentTerminal = await LoadAssetAsync<PaymentTerminal>("Popups/Terminal", parent);
        }

        if (paymentTerminal != null) paymentTerminal.Check(value, OnCorrect, OnIncorrect);
    }

    public async UniTaskVoid MoneyChange(float value, Action<float, float> OnGet, Action OnReset, Action OnCorrect, Action OnIncorrect)
    {
        if (moneyChanger == null)
        {
            moneyChanger = await LoadAssetAsync<MoneyChanger>("Popups/MoneyChanger", parent);
        }

        if (moneyChanger != null) moneyChanger.Check(value, OnGet, OnReset, OnCorrect, OnIncorrect);
    }

    public async UniTaskVoid SellPopup(Action OnConfirm)
    {
        if (sellPopup == null)
        {
            sellPopup = await LoadAssetAsync<SellPanel>("Popups/Sell Popup", parent);
        }

        if (sellPopup != null) sellPopup.Sell(OnConfirm);
    }

    private async static UniTask<T> LoadAssetAsync<T>(string resourcePath, Transform parent = null) where T : UnityEngine.Object
    {
        var request = Resources.LoadAsync<GameObject>(resourcePath);

        await request;

        var prefab = request.asset as GameObject;
        if (prefab == null)
        {
            Debug.LogWarning("[PopUps] Prefab non trovato in Resources/" + resourcePath);
            return null;
        }

        var go = Instantiate(prefab);
        go.SetActive(false);
        if (parent)
        {
            go.transform.SetParent(parent, false);
            go.transform.SetSiblingIndex(parent.childCount - 2);
        }

        return go.GetComponent<T>();
    }

#if UNITY_EDITOR
    [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.SubsystemRegistration)]
    private static void ReloadDomain()
    {
        paymentTerminal = null;
        moneyChanger = null;
        sellPopup = null;
    }
#endif
}
