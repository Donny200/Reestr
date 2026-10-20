package adliya.uz.functioncatalogservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicSiteUrlTest {

    @Test void configuredPublicAddressIsUsedWithoutTrailingSlash() {
        assertThat(PublicSiteUrl.canonical("https://reestr.gov.uz/")).isEqualTo("https://reestr.gov.uz");
        assertThat(PublicSiteUrl.page("https://reestr.gov.uz", "/functions/3")).isEqualTo("https://reestr.gov.uz/functions/3");
    }

    @Test void developmentAndPrivateAddressesAreNeverPrinted() {
        for (String value : new String[]{"http://localhost:5173", "http://localhost", "http://127.0.0.1:3000", "http://0.0.0.0",
                "http://[::1]:8080", "http://192.168.1.10", "http://10.0.0.5", "http://172.20.0.3", "http://front", "http://app.local",
                "http://dev.localhost:3000", "ftp://reestr.gov.uz", "not a url", "", "   ", null}) {
            assertThat(PublicSiteUrl.canonical(value)).as(String.valueOf(value)).isNull();
            assertThat(PublicSiteUrl.page(value, "/functions/3")).as(String.valueOf(value)).isNull();
        }
        assertThat(PublicSiteUrl.canonical("http://172.15.0.1")).isEqualTo("http://172.15.0.1");
    }
}
