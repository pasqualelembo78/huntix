using UnityEditor;
using UnityEditor.UIElements;
using UnityEngine.UIElements;

namespace Models.Editor
{
	[CustomPropertyDrawer(typeof(Observable<>),true)]
	public class ObservableDrawer : PropertyDrawer
	{
		public override VisualElement CreatePropertyGUI(SerializedProperty property)
		{
			return new PropertyField(property.FindPropertyRelative("value"), property.displayName);
		}
	}
}