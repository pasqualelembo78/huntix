#!/usr/bin/env python3
# Test statici dei guard anti-NAN aggiunti dopo il log 2026-11-25: il NaN del
# player non deve piu' propagarsi a origine, chunk, mesh e GPS. Nessun device:
# verifichiamo che le barriere esistano nei punti esatti della catena.
import os
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SCRIPTS = 'unity-project/Assets/City/Scripts/'


def read(path):
    with open(os.path.join(ROOT, path), encoding='utf-8', errors='replace') as f:
        return f.read()


class TestFiniteHelpers(unittest.TestCase):
    def test_world_origin_espone_is_finite_per_float_double_vector3(self):
        src = read(SCRIPTS + 'OSM/WorldOrigin.cs')
        self.assertIn('public static bool IsFinite(float v)', src)
        self.assertIn('public static bool IsFinite(double v)', src)
        self.assertIn('public static bool IsFinite(Vector3 v)', src)
        self.assertIn('!float.IsNaN(v) && !float.IsInfinity(v)', src)

    def test_init_e_rebase_bloccano_valori_non_finiti(self):
        src = read(SCRIPTS + 'OSM/WorldOrigin.cs')
        self.assertIn('[WorldOrigin] INIT NON VALIDO ignorato', src)
        self.assertIn('[WorldOrigin] REBASE BLOCCATO: posizione player non finita', src)
        self.assertIn('[WorldOrigin] REBASE BLOCCATO: origine calcolata non finita', src)
        # La condizione originaria `NaN < RebaseDistanceM` era il bug.
        self.assertIn('float dist = DistanceFromOrigin(playerWorld);', src)
        self.assertIn('if (!IsFinite(dist) || dist < RebaseDistanceM)', src)


class TestChunkManagerPropagation(unittest.TestCase):
    def test_tick_e_update_ripristinano_il_target_invalido(self):
        src = read(SCRIPTS + 'OSM/ChunkManager.cs')
        self.assertIn('private Vector3 _lastGoodTargetPos;', src)
        self.assertIn('private void RescueInvalidTarget()', src)
        self.assertIn('_lastGoodTargetPos = target.position;', src)
        self.assertGreaterEqual(src.count('RescueInvalidTarget();'), 2)
        self.assertIn('[ChunkManager] SALVATAGGIO target non finito', src)

    def test_on_rebased_ignora_delta_non_finito_e_usa_finally(self):
        src = read(SCRIPTS + 'OSM/ChunkManager.cs')
        self.assertIn('[ChunkManager] REBASE IGNORATO: delta non finito', src)
        self.assertIn('if (!WorldOrigin.IsFinite(delta))', src)
        self.assertIn('if (cc != null && ccWasEnabled) cc.enabled = true;', src)


class TestGroundProbes(unittest.TestCase):
    def test_groundsnapper_filtra_origine_hit_e_follower(self):
        src = read(SCRIPTS + 'OSM/GroundSnapper.cs')
        self.assertIn('if (!WorldOrigin.IsFinite(center) || !WorldOrigin.IsFinite(side) ||', src)
        # +Infinity e' il maxSurfaceY legittimo dello snap di spawn.
        self.assertIn('float.IsNaN(maxSurfaceY) || float.IsNegativeInfinity(maxSurfaceY)', src)
        self.assertIn('if (!WorldOrigin.IsFinite(local.point.y) ||', src)
        self.assertIn('if (!WorldOrigin.IsFinite(pos)) return GroundSample.Miss;', src)
        self.assertIn('if (!WorldOrigin.IsFinite(st.vel)) st.vel = 0f;', src)

    def test_try_ground_column_non_restituisce_snap_non_finito(self):
        src = read(SCRIPTS + 'OSM/CityChunkedWorld.cs')
        self.assertIn('if (!WorldOrigin.IsFinite(worldPos) || !WorldOrigin.IsFinite(clearance) ||', src)
        self.assertIn('if (!WorldOrigin.IsFinite(best.point.y) || best.collider == null)', src)
        self.assertIn('if (!WorldOrigin.IsFinite(snapped)) return false;', src)
        self.assertIn('if (!WorldOrigin.IsFinite(dem)) dem = 0f;', src)


class TestPlayerRecovery(unittest.TestCase):
    def test_player_controller_torna_all_ultima_posizione_finita(self):
        src = read(SCRIPTS + 'Player/PlayerController.cs')
        self.assertIn('private Vector3 _lastGoodPlayerPos;', src)
        self.assertIn('private void RescueInvalidPlayer()', src)
        self.assertIn('[PlayerController] SALVATAGGIO NaN', src)
        self.assertIn('_lastGoodPlayerPos = transform.position;', src)
        self.assertIn('!Finite(transform.rotation)', src)
        self.assertGreaterEqual(src.count('RescueInvalidPlayer();'), 5)
        # Il QA 26/11 ha mostrato un loop: il guard rilevava un campo non
        # finito ma il salvataggio non azzerava moveInput/rotazione, quindi
        # scattava di nuovo a ogni frame. Ora vengono ripristinati entrambi.
        self.assertIn('moveInput = Vector2.zero;', src)
        self.assertIn('if (!Finite(transform.rotation)) transform.rotation = safeRot;', src)
        self.assertIn('causa=[', src)

    def test_guard_subito_dopo_controller_move(self):
        src = read(SCRIPTS + 'Player/PlayerController.cs')
        move = src.find('controller.Move(velocity * stepDt);')
        self.assertGreater(move, 0)
        after = src[move:move + 600]
        self.assertIn('if (!Finite(transform.position))', after)
        self.assertIn('RescueInvalidPlayer();', after)
        # Il percorso finito resta invariato: niente cambi di interpolation.
        vehicle = read(SCRIPTS + 'Vehicle/VehicleController.cs')
        self.assertIn('rb.interpolation = RigidbodyInterpolation.Interpolate;', vehicle)


class TestJoystickFiniteInput(unittest.TestCase):
    def test_drag_non_finito_non_avvelena_l_input(self):
        expected_zero = {
            'UI/DynamicJoystick.cs': 'Value = Vector2.zero;',
            'UI/AlternateTouchInput.cs': 'target = Vector2.zero;',
        }
        for path, zero in expected_zero.items():
            src = read(SCRIPTS + path)
            self.assertIn('!WorldOrigin.IsFinite(pos.x)', src, path)
            self.assertIn('!WorldOrigin.IsFinite(origin.x)', src, path)
            self.assertIn(zero, src, path)


class TestFootIkSurfaceFilter(unittest.TestCase):
    def test_il_ray_ik_non_prende_tetti_e_auto(self):
        # Log 26/11: footLift sale a 0,92/1,01/1,10 m con bestY≈0,98 (tetto
        # di un'auto parcheggiata). Le caviglie IK finivano vicino al bacino e
        # il personaggio piegava le gambe per 2-3 frame all'avvio.
        src = read(SCRIPTS + 'Player/CharacterWalker.cs')
        self.assertIn('int mask = groundMask.value & GroundSnapper.WalkableMask;', src)
        self.assertIn('float maxSurfaceY = groundRef + GroundSnapper.ShortMaxRise;', src)
        self.assertIn('if (h.point.y > maxSurfaceY) continue;', src)
        self.assertNotIn(
            'Physics.RaycastNonAlloc(ray, IkHitsBuffer, maxDist, groundMask,', src)
        # La sonda del piede usa gia' WalkableMask e il tetto +0,35 m: la
        # stessa regola vale ora per il target IK del bacino/caviglie.
        foot = read(SCRIPTS + 'Player/FootIK.cs')
        self.assertIn('GroundSnapper.WalkableMask,', foot)
        self.assertIn('MaxPlant', foot)


if __name__ == '__main__':
    unittest.main()
