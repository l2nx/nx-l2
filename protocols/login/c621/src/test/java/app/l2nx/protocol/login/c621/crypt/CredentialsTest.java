/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.login.c621.crypt.Credentials.Layout;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CredentialsTest {
    private static final KeyPair KEYS = keyPair();

    @Nested
    class Decrypt {
        @Test
        void decrypt_shouldReadTheLoginAndPassword_atTheOneBlockOffsets() throws Exception {
            final byte[] plain = new byte[128];
            put(plain, 0x5E, "test1");
            put(plain, 0x6C, "secret");
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(rsa(plain, 0)).build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual).isEqualTo(credentials("test1", "secret"));
        }

        @Test
        void decrypt_shouldJoinTheLoginParts_atTheTwoBlockOffsets() throws Exception {
            final byte[] plain = new byte[256];
            put(plain, 0x4E, "a".repeat(50));
            put(plain, 0xCE, "b".repeat(3));
            put(plain, 0xDC, "secret");
            final RequestAuthLogin packet = RequestAuthLogin.builder()
                    .block1(rsa(plain, 0))
                    .block2(rsa(plain, 128))
                    .build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual).isEqualTo(credentials("a".repeat(50) + "bbb", "secret"));
        }

        @Test
        void decrypt_shouldReadTheLoginAcrossBothHalves_whenItTakesAllSixtyFourBytes() throws Exception {
            final byte[] plain = new byte[256];
            put(plain, 0x4E, "h".repeat(50));
            put(plain, 0xCE, "t".repeat(14));
            put(plain, 0xDC, "p".repeat(16));
            final RequestAuthLogin packet = RequestAuthLogin.builder()
                    .block1(rsa(plain, 0))
                    .block2(rsa(plain, 128))
                    .build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual).isEqualTo(credentials("h".repeat(50) + "t".repeat(14), "p".repeat(16)));
        }

        @Test
        void decrypt_shouldTrimTheEdgesOfTheJoinedLogin_andKeepTheSpacesInside() throws Exception {
            final byte[] plain = new byte[256];
            put(plain, 0x4E, " " + "a".repeat(48) + " ");
            put(plain, 0xCE, " b ");
            put(plain, 0xDC, " pw ");
            final RequestAuthLogin packet = RequestAuthLogin.builder()
                    .block1(rsa(plain, 0))
                    .block2(rsa(plain, 128))
                    .build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual).isEqualTo(credentials("a".repeat(48) + "  b", "pw"));
        }

        @ParameterizedTest(name = "{0}: [{1}] / [{2}]")
        @CsvSource({"ONE_BLOCK, 'admin ', ' secret', admin, secret", "TWO_BLOCKS, ' admin', 'secret ', admin, secret"})
        void decrypt_shouldTrimTheFields(
                Layout layout, String user, String password, String expectedUser, String expectedPassword)
                throws Exception {
            final RequestAuthLogin packet = credentials(user, password).encrypt(KEYS.getPublic(), layout);

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual.user()).isEqualTo(expectedUser);
            assertThat(actual.password()).isEqualTo(expectedPassword);
        }

        @Test
        void decrypt_shouldReadABlankFieldAsNull() throws Exception {
            final byte[] plain = new byte[128];
            put(plain, 0x5E, "   ");
            put(plain, 0x6C, "\t \t");
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(rsa(plain, 0)).build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual.user()).isNull();
            assertThat(actual.password()).isNull();
        }

        @Test
        void decrypt_shouldReadNull_whenAFieldIsNotUtf8() throws Exception {
            final byte[] user = "тест".getBytes(Charset.forName("windows-1251"));
            final byte[] plain = new byte[128];
            System.arraycopy(user, 0, plain, 0x5E, user.length);
            put(plain, 0x6C, "secret");
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(rsa(plain, 0)).build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual.user()).isNull();
            assertThat(actual.password()).isEqualTo("secret");
        }

        @Test
        void decrypt_shouldJoinACharacterSplit_acrossTheHeadAndTheTail() throws Exception {
            final byte[] user = ("a".repeat(49) + "ж").getBytes(StandardCharsets.UTF_8);
            final byte[] plain = new byte[256];
            System.arraycopy(user, 0, plain, 0x4E, 50);
            System.arraycopy(user, 50, plain, 0xCE, 1);
            put(plain, 0xDC, "secret");
            final RequestAuthLogin packet = RequestAuthLogin.builder()
                    .block1(rsa(plain, 0))
                    .block2(rsa(plain, 128))
                    .build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual).isEqualTo(credentials("a".repeat(49) + "ж", "secret"));
        }

        @Test
        void decrypt_shouldStopTheLoginAtTheHead_whenTheHeadEndsWithAZero() throws Exception {
            final byte[] plain = new byte[256];
            put(plain, 0x4E, "head");
            put(plain, 0xCE, "tail");
            put(plain, 0xDC, "secret");
            final RequestAuthLogin packet = RequestAuthLogin.builder()
                    .block1(rsa(plain, 0))
                    .block2(rsa(plain, 128))
                    .build();

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual).isEqualTo(credentials("head", "secret"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Layout.class)
        void decrypt_shouldReadNull_whenTheFieldsAreEmpty(Layout layout) throws Exception {
            final RequestAuthLogin packet = credentials(null, null).encrypt(KEYS.getPublic(), layout);

            final Credentials actual = Credentials.decrypt(KEYS.getPrivate(), packet);

            assertThat(actual.user()).isNull();
            assertThat(actual.password()).isNull();
        }

        @Test
        void decrypt_shouldThrowBadPadding_whenTheBlockIsLargerThanTheModulus() {
            final byte[] larger = new byte[128];
            Arrays.fill(larger, (byte) 0xFF);
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(larger).build();

            assertThatThrownBy(() -> Credentials.decrypt(KEYS.getPrivate(), packet))
                    .isInstanceOf(BadPaddingException.class);
        }
    }

    @Nested
    class Encrypt {
        @ParameterizedTest(name = "{0}: {1} / {2}")
        @CsvSource({
            "ONE_BLOCK, test1, secret",
            "ONE_BLOCK, fourteen_bytes, sixteen_bytes_pw",
            "TWO_BLOCKS, test1, secret",
            "TWO_BLOCKS, a_login_of_sixty_four_bytes_that_spans_both_rsa_blocks_of_256byt, sixteen_bytes_pw",
            "ONE_BLOCK, a b, p w",
            "TWO_BLOCKS, 'a login with spaces that crosses byte fifty of it  ok', p w",
            "ONE_BLOCK, тест, пароль",
            "TWO_BLOCKS, a split character crosses byte fifty of the loginж, пароль12",
            "TWO_BLOCKS, логин_из_тридцати_символов, emoji😀pw"
        })
        void encrypt_shouldRoundTripThroughDecrypt(Layout layout, String user, String password) throws Exception {
            final Credentials expected = credentials(user, password);

            final RequestAuthLogin packet = expected.encrypt(KEYS.getPublic(), layout);

            assertThat(packet.block2() != null).isEqualTo(layout == Layout.TWO_BLOCKS);
            assertThat(Credentials.decrypt(KEYS.getPrivate(), packet)).isEqualTo(expected);
        }

        @Test
        void encrypt_shouldUseTheKeyOfTheScrambledModulus() throws Exception {
            final byte[] scrambled = ScrambledModulus.scramble(((RSAPublicKey) KEYS.getPublic()).getModulus());

            final RequestAuthLogin packet =
                    credentials("test1", "secret").encrypt(ScrambledModulus.publicKey(scrambled), Layout.ONE_BLOCK);

            assertThat(Credentials.decrypt(KEYS.getPrivate(), packet)).isEqualTo(credentials("test1", "secret"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Layout.class)
        void encrypt_shouldRefuse_whenThePasswordIsLongerThanSixteenBytes(Layout layout) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> credentials("test1", "p".repeat(17)).encrypt(KEYS.getPublic(), layout));
        }

        @ParameterizedTest(name = "{0}: {1} bytes")
        @CsvSource({"ONE_BLOCK, 15", "TWO_BLOCKS, 65"})
        void encrypt_shouldRefuse_whenTheLoginDoesNotFit(Layout layout, int length) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> credentials("u".repeat(length), "secret").encrypt(KEYS.getPublic(), layout));
        }

        @ParameterizedTest(name = "{0}: {1} / {2}")
        @CsvSource({"ONE_BLOCK, тестовый, secret", "TWO_BLOCKS, test1, пароль_пароль"})
        void encrypt_shouldRefuse_whenTheUtf8BytesDoNotFit(Layout layout, String user, String password) {
            final Credentials credentials = credentials(user, password);

            assertThatIllegalArgumentException().isThrownBy(() -> credentials.encrypt(KEYS.getPublic(), layout));
        }

        @ParameterizedTest(name = "{0} / {1}")
        @CsvSource({"'te\0st', secret", "test1, 'pw\uD800'"})
        void encrypt_shouldRefuse_whenTheTextHasAZeroOrAnUnpairedSurrogate(String user, String password) {
            final Credentials credentials = credentials(user, password);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> credentials.encrypt(KEYS.getPublic(), Layout.TWO_BLOCKS));
        }
    }

    @Nested
    class Fits {
        @ParameterizedTest(name = "{0}: [{1}] / [{2}] -> {3}")
        @CsvSource({
            "ONE_BLOCK, test1, secret, true",
            "ONE_BLOCK, uuuuuuuuuuuuuu, secret, true",
            "ONE_BLOCK, uuuuuuuuuuuuuuu, secret, false",
            "TWO_BLOCKS, uuuuuuuuuuuuuuu, secret, true",
            "ONE_BLOCK, тестовый, secret, false",
            "ONE_BLOCK, test1, pppppppppppppppp, true",
            "ONE_BLOCK, test1, ppppppppppppppppp, false",
            "ONE_BLOCK, 'te\0st', secret, false",
            "ONE_BLOCK, test1, 'pw\uD800', false"
        })
        void fits_shouldMatchWhatEncryptAccepts(Layout layout, String user, String password, boolean expected) {
            assertThat(credentials(user, password).fits(layout)).isEqualTo(expected);
        }
    }

    @Nested
    class Construct {
        @ParameterizedTest(name = "user {0}, password {1}")
        @CsvSource({"'', secret", "test1, ''", "'   ', secret", "test1, '  '"})
        void credentials_shouldRefuseABlankField(String user, String password) {
            assertThatIllegalArgumentException().isThrownBy(() -> credentials(user, password));
        }
    }

    @Nested
    class ToString {
        @Test
        void toString_shouldNotPrintThePassword() {
            assertThat(credentials("test1", "secret").toString()).doesNotContain("secret");
        }
    }

    private static Credentials credentials(String user, String password) {
        return Credentials.builder().user(user).password(password).build();
    }

    private static void put(byte[] block, int offset, String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, block, offset, bytes.length);
    }

    private static byte[] rsa(byte[] plain, int offset) throws Exception {
        final Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, KEYS.getPublic());
        return cipher.doFinal(plain, offset, 128);
    }

    private static KeyPair keyPair() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
