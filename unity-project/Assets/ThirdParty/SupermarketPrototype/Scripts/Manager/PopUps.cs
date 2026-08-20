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

    public GameObject terminalPrefab;
    public GameObject moneyChangerPrefab;
    public GameObject sellPopupPrefab;

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

    private async UniTask<T> LoadAssetAsync<T>(string resourcePath, Transform parent = null) where T : UnityEngine.Object
    {
        var prefab = InstancePrefabFor(resourcePath);
        if (prefab == null)
        {
            var request = Resources.LoadAsync<GameObject>(resourcePath);

            await request;

            prefab = request.asset as GameObject;
        }

        if (prefab == null)
        {
            Debug.LogWarning("[PopUps] Prefab non trovato (risorsa non disponibile nel build Android): " + resourcePath);
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

    private GameObject InstancePrefabFor(string resourcePath)
    {
        if (resourcePath == "Popups/Terminal") return terminalPrefab;
        if (resourcePath == "Popups/MoneyChanger") return moneyChangerPrefab;
        if (resourcePath == "Popups/Sell Popup") return sellPopupPrefab;
        return null;
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
