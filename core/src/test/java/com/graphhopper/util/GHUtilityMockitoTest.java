package com.graphhopper.util;

import com.graphhopper.routing.weighting.Weighting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Titouan Johanny
 */
@ExtendWith(MockitoExtension.class)
class GHUtilityMockitoTest {

    @Mock
    Weighting weighting;
    @Mock
    EdgeIteratorState edge;

    /**
     * calcWeightWithTurnWeight_adds_turn_forward_and_reverse
     * on cherche à vérifier l’addition correcte du turnWeight selon le sens
     * edgeWeight=10; turnFwd=2.5; turnRev=3.0; baseNode=7; edgeId=99; prev/next=42.
     * Oracle :
     * - reverse=false, prev=-1 -> 10.0
     * - reverse=false, prev=42 -> 12.5
     * - reverse=true,  next=42 -> 13.0
     * <p>
     * On teste différentes branches -> suppression/inversion du test “prevOrNextEdgeId valide ?”.
     * On effectue une permutation des paramètres de calcTurnWeight.
     * On veut retourner les mutants qui renvoient seulement edgeWeight (en omettant l'addition).
     * On veut aussi détecter les mutants qui appelleraient turnWeight avec un autre node.
     * <p>
     * Limites :
     * On ne valide pas la cohérence de Weighting.
     * On vérifie uniquement que GHUtility effectue correctement ses appels et combine
     * correctement les valeurs retournées.
     * <p>
     * valeurs numériques stables et distinctes.
     * verify() sur les appels de turn pour couvrir l’ordre des paramètres et le sens.
     * tolérance numérique serrée (1e-9).
     */
    @Test
    @DisplayName("calcWeightWithTurnWeight: additionne correctement le turnWeight en forward et reverse")
    void calcWeightWithTurnWeight_adds_turn_forward_and_reverse() {
        when(edge.getBaseNode()).thenReturn(7);
        when(edge.getEdge()).thenReturn(99);

        // poids d'arrête identique dans les deux sens --> isoler turn
        when(weighting.calcEdgeWeight(edge, false)).thenReturn(10.0);
        when(weighting.calcEdgeWeight(edge, true)).thenReturn(10.0);

        // forward: turn(prevEdgeId, base, edgeId)
        when(weighting.calcTurnWeight(42, 7, 99)).thenReturn(2.5);
        // reverse: turn(edgeId, base, nextEdgeId)
        when(weighting.calcTurnWeight(99, 7, 42)).thenReturn(3.0);

        // cas sans turn
        assertEquals(10.0, GHUtility.calcWeightWithTurnWeight(weighting, edge, false, -1), 1e-9);
        // turn forward
        assertEquals(12.5, GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 42), 1e-9);
        // turn reverse
        assertEquals(13.0, GHUtility.calcWeightWithTurnWeight(weighting, edge, true, 42), 1e-9);

        // vérifie -> deux variantes avec les bons paramètres
        verify(weighting).calcTurnWeight(42, 7, 99);
        verify(weighting).calcTurnWeight(99, 7, 42);

        // aucune autre interaction imprévue
        verifyNoMoreInteractions(weighting);
    }
}
