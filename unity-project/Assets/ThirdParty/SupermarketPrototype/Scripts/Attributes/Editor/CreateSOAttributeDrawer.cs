using UnityEngine;
using UnityEditor;
using System.Reflection;
using Supermarket.Products;

#if UNITY_EDITOR
[CustomPropertyDrawer(typeof(NewProductAttribute))]
public class CreateSOAttributeDrawer : PropertyDrawer
{
    public override void OnGUI(Rect position, SerializedProperty property, GUIContent label)
    {
        EditorGUI.BeginProperty(position, label, property);
        position.width -= 80;
        EditorGUI.ObjectField(position, property, label);

        position.x += position.width;
        position.width = 80;
        if (GUI.Button(position, new GUIContent("New")))
        {
            ProductInfoCreator.OpenWindow((productInfo) =>
            {
                if (productInfo)
                {
                    property.objectReferenceValue = productInfo;
                    property.serializedObject.ApplyModifiedProperties();
                }
            });

        }
        EditorGUI.EndProperty();
    }

    void SetValue(ProductInfo productInfo, string fieldName, object value)
    {
        FieldInfo f = productInfo.GetType().GetField(fieldName, BindingFlags.NonPublic | BindingFlags.Instance | BindingFlags.Public);
        if (f != null)
        {
            f.SetValue(productInfo, value);
        }
    }
}
#endif
