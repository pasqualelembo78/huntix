"""Guardie statiche per lavori, carriera, abilità e furti di MiCittà (26/11/2026).

Verifica che le nuove funzionalità siano collegate a Unity, Android e al
riflesso uova, senza aver disattivato i sistemi preesistenti (XP locale dei
lavori, abilità caccia-uova, concessionaria, officina, motore furti server).

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_city_skills.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNITY = os.path.join(ROOT, "unity-project", "Assets")
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "intelligame", "huntix")
BACKEND = os.path.join(ROOT, "backend", "traffic")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestCarrieraLavori(unittest.TestCase):
    def setUp(self):
        self.job = read(os.path.join(
            UNITY, "City", "Scripts", "Economy", "JobManager.cs"))
        self.bridge = read(os.path.join(APP, "bridge", "Bridge.kt"))
        self.profile = read(os.path.join(APP, "PlayerProfile.kt"))

    def test_un_lavoro_completato_sale_di_un_livello(self):
        # Il livello che vede l'utente è PlayerProfile.level: la soglia è lv*150
        # e un solo lavoro accredita esattamente il fabbisogno mancante.
        self.assertIn('val needed = maxOf(1L, profile.xpForNextLevel - profile.xp)',
                      self.bridge)
        self.assertIn('addXpFromCity(needed)', self.bridge)
        self.assertIn('after = profile.level', self.bridge)
        self.assertIn("val xpForNextLevel: Long get()", self.profile)
        self.assertIn('NotifyJobCompleted(playerPos, _job.def.title, paid,',
                      self.job)
        self.assertIn('"CityJobCompleted"', self.job)

    def test_paga_cresce_col_livello_senza_toccare_quella_storica(self):
        self.assertIn('private const float CareerPayStep = 0.10f;', self.job)
        self.assertIn('private const int CareerPayMaxLevel = 50;', self.job)
        self.assertIn('pay *= PayMult(_job.def.type) * CareerPayMult();', self.job)
        # many many many: il +15% del livello del singolo lavoro resta invariato.
        self.assertIn('return 1f + 0.15f * (Level(t) - 1);', self.job)
        self.assertIn('livello " + livelloCarriera + " → "', self.job)

    def test_xp_locale_dei_lavori_non_viene_rimosso(self):
        self.assertIn('private const string XpKeyPrefix = "city_job_xp_";', self.job)
        self.assertIn('XpKeyPrefix + _job.def.type', self.job)
        self.assertIn('SyncXpToHuntix(xpGain + 10,', self.job)


class TestAbilitaMiCitta(unittest.TestCase):
    def setUp(self):
        self.manager = read(os.path.join(
            APP, "gamification", "CitySkillManager.kt"))
        self.mirror = read(os.path.join(
            UNITY, "City", "Scripts", "Economy", "CitySkills.cs"))
        self.ability = read(os.path.join(APP, "ui", "AbilityActivity.kt"))
        self.activity = read(os.path.join(APP, "bridge", "BridgeActivity.java"))
        self.game = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))

    def test_nuovi_utenti_partono_da_abilita_zero(self):
        self.assertIn('const val MAX_LEVEL = 10', self.manager)
        self.assertIn('readLevels(ctx).optInt(id, 0).coerceIn(0, MAX_LEVEL)',
                      self.manager)
        self.assertNotIn('optInt(id, 1)', self.manager)
        self.assertIn('fun addPoints(ctx: Context, amount: Int): Int',
                      self.manager)
        self.assertIn('fun spendPoint(ctx: Context, id: String): Boolean',
                      self.manager)

    def test_abilita_proposte_e_gate_del_ladro(self):
        for skill in ("mastro_ladro", "pilota", "meccanico", "attore",
                      "assassino", "contabile"):
            self.assertIn('"' + skill + '"', self.manager)
        self.assertIn('public const string ThiefId = "mastro_ladro";', self.mirror)
        self.assertIn('public const int ApartmentTheftLevel = 10;', self.mirror)
        self.assertIn('if (price <= 60) return 2;', self.mirror)
        self.assertIn('if (price <= 100) return 4;', self.mirror)
        self.assertIn('if (price <= 130) return 6;', self.mirror)
        self.assertIn('return 8;', self.mirror)

    def test_unita_android_e_unita_unity_sono_collegate(self):
        self.assertIn('j.put("citySkills", skills.exportLevelsString(this));',
                      self.activity)
        self.assertIn('j.put("citySkillPoints", skills.points(this));',
                      self.activity)
        self.assertIn('"CitySkillsUpdated"', self.game)
        self.assertIn('ApplyCitySkills(jsonData)', self.game)
        self.assertIn('case "CitySkillsUpdated"', self.game)
        self.assertIn('City.Economy.CitySkills.Restore(levels, points);',
                      self.game)
        self.assertIn('sendToUnity(\n                "GameManager", "OnEvent",\n'
                      '                "CitySkillsUpdated|"', self.manager)
        self.assertIn('Mestieri di MiCittà', self.ability)

    def test_abilita_caccia_uova_restano_intatte(self):
        ability_manager = read(os.path.join(
            APP, "gamification", "AbilityManager.kt"))
        self.assertIn('canUpgrade', ability_manager)
        self.assertIn('AbilityManager.loadAbilities(playerLevel)', self.ability)
        self.assertIn('AbilityManager.useAbility(ability', self.ability)


class TestFurtoAuto(unittest.TestCase):
    def setUp(self):
        self.theft = read(os.path.join(
            UNITY, "City", "Scripts", "Vehicle", "TheftUI.cs"))
        self.dealer = read(os.path.join(
            UNITY, "City", "Scripts", "Vehicle", "DealershipUI.cs"))
        self.officina = read(os.path.join(
            UNITY, "City", "Scripts", "Vehicle", "OfficinaUI.cs"))
        self.server = read(os.path.join(BACKEND, "vehicle_services.py"))

    def test_gate_e_consegna_permanente_senza_pagamento(self):
        self.assertIn('CitySkills.ThiefLevel <', self.theft)
        self.assertIn('CitySkills.TheftRequiredLevel(def.name, def.price)',
                      self.theft)
        self.assertIn('.Buy(code, (ok, err) =>', self.theft)
        self.assertIn('Inventory.Add("vehicle_" + code);', self.theft)
        self.assertIn('Game.Instance.EnterVehicle(vc);', self.theft)
        # Nessun Wallet.Spend: l'auto rubata non costa nulla e non scade.
        self.assertNotIn('Wallet.Spend', self.theft)

    def test_pulsante_furto_raggiungibile_da_concessionaria_e_officina(self):
        self.assertIn('RUB AUTO (Mastro Ladro Lv.', self.dealer)
        self.assertIn('RUB AUTO (Mastro Ladro Lv.', self.officina)
        self.assertIn('TheftUI.Open()', self.dealer)
        self.assertIn('TheftUI.Open()', self.officina)
        self.assertIn('COMPRA E RITIRA', self.dealer)
        self.assertIn('ANTIFURTI', self.officina)

    def test_motore_furti_del_server_non_riscritto(self):
        self.assertIn('def _steal(v: dict, code: str, when: float)', self.server)
        self.assertIn('ransom_deadline', self.server)
        self.assertIn('found_abandoned', self.server)


class TestRiflessoUova(unittest.TestCase):
    def setUp(self):
        self.job = read(os.path.join(
            UNITY, "City", "Scripts", "Economy", "JobManager.cs"))
        self.theft = read(os.path.join(
            UNITY, "City", "Scripts", "Vehicle", "TheftUI.cs"))
        self.bridge = read(os.path.join(APP, "bridge", "Bridge.kt"))
        self.ipc = read(os.path.join(APP, "bridge", "UnityIpc.kt"))
        self.journal = read(os.path.join(
            APP, "managers", "EggWhereLog.kt"))

    def test_eventi_nella_whitelist_del_bridge(self):
        self.assertIn('"CityJobCompleted", "CityVehicleStolen"', self.ipc)
        self.assertIn('"CityJobCompleted" -> handleCityJobCompleted(jsonData)',
                      self.bridge)
        self.assertIn('"CityVehicleStolen" -> handleCityVehicleStolen(jsonData)',
                      self.bridge)

    def test_luogo_testuale_e_coordinate_nel_diario(self):
        # Nei sorgenti C# le virgolette del JSON sono escaped (\\\").
        for field in ('place', 'lat', 'lng'):
            self.assertIn('\\"' + field + '\\"', self.job)
            self.assertIn('\\"' + field + '\\"', self.theft)
        self.assertIn('MiCittà→lavori→', self.job)
        self.assertIn('MiCittà→garage→furto→', self.theft)
        self.assertIn('EggWhereLog.recordActivity(', self.bridge)
        self.assertIn('EggWhereLog.record(', self.bridge)
        self.assertIn('fun record(ctx: Context, rarityId: String, place: String',
                      self.journal)
        self.assertIn('put("lat", lat)', self.journal)
        self.assertIn('put("lng", lng)', self.journal)
        self.assertIn('\\"eggSpawned\\"', self.job)
        self.assertIn('\\"eggRarity\\"', self.job)


if __name__ == "__main__":
    unittest.main(verbosity=2)
