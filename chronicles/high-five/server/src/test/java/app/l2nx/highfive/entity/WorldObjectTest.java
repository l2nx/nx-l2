package app.l2nx.highfive.entity;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WorldObjectTest {

    private static WorldObject objectWithId(int objectId) {
        return new WorldObject(objectId) {
            @Override
            public boolean isAutoAttackable(Creature attacker) {
                return false;
            }

            @Override
            public void sendInfo(Player player) {}
        };
    }

    @Nested
    class HashCodeContract {

        @Test
        void hashCode_shouldMatch_whenObjectIdsEqual() {
            WorldObject first = objectWithId(268_435_456);
            WorldObject second = objectWithId(268_435_456);

            assertThat(first).isEqualTo(second);
            assertThat(first.hashCode()).isEqualTo(second.hashCode());
        }

        @Test
        void contains_shouldFindEqualInstance_whenHashSetHoldsOther() {
            Set<WorldObject> objects = new HashSet<>();
            objects.add(objectWithId(268_435_457));

            assertThat(objects.contains(objectWithId(268_435_457))).isTrue();
        }

        @Test
        void equals_shouldBeFalse_whenObjectIdsDiffer() {
            assertThat(objectWithId(1)).isNotEqualTo(objectWithId(2));
        }
    }
}
