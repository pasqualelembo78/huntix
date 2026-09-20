using UnityEditor;

// Il singolo ruolo e: far ricompilare TUTTI gli script in batch mode
// (-batchmode -executeMethod CompileCheck.Run -quit) e poi verificare nel
// log l assenza di "error CS". Senza metodo il processo esce prima della
// compilazione e non si valuta nulla.
public static class CompileCheck
{
    public static void Run()
    {
        UnityEngine.Debug.Log("CompileCheck ok - nessun errore CS");
    }
}
