using UnityEngine;

namespace City.Interior.Visit
{
    /// <summary>
    /// Nascondiglio uovo dentro una casa visitabile (Riflesso del Mandato).
    /// È messo sui contenitori CC0 dal decorator: quando il giocatore entra,
    /// `VisitInteriorManager` assegna l'uovo-feature nel punto reale del
    /// contenitore con il luogo testuale "casa→stanza→oggetto".
    ///
    /// La dedup è in `HouseInteriorState` (snapshot Android), quindi rebuild
    /// dei chunk e restore non generano uova duplicate.
    /// </summary>
    public sealed class HouseEggSpot : MonoBehaviour
    {
        public string featureId = "";
        public string label = "";
        public string place = "";
        public string room = "";

        /// <summary>Posizionemondo dell'uovo: appena davanti/sopra il contenitore.</summary>
        public Vector3 EggPosition
        {
            get { return transform.position + Vector3.up * 0.55f + transform.forward * 0.25f; }
        }
    }
}
