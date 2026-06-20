# visual-assert

Libreria Maven per confronti visuali custom in Java.

## Scope

La libreria riceve immagini prodotte dal progetto chiamante e restituisce risultati strutturati. Non apre browser, non esegue test e non dipende da runner o framework E2E.

## Dipendenza Maven

```xml
<dependency>
    <groupId>it.aruba.qaa</groupId>
    <artifactId>visual-assert</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

La libreria non dichiara dipendenze esterne.

## Baseline

Nel progetto E2E la baseline deve trovarsi sotto:

```text
src/test/resources/test/cv_img
```

La chiave `checkout-summary` risolve automaticamente:

```text
test/cv_img/checkout-summary.png
test/cv_img/checkout-summary.jpg
test/cv_img/checkout-summary.jpeg
```

## Compare

L'unico entrypoint di confronto e':

```java
import it.aruba.qaa.cv.VisualAssert;
import it.aruba.qaa.cv.VisualCompareOptions;
import it.aruba.qaa.cv.VisualCompareResult;
import it.aruba.qaa.cv.VisualRegion;

byte[] screenshotBytes = captureScreenshotBytes();

VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary",
        VisualCompareOptions.builder()
                .artifactName("checkout-summary")
                .maxDiffPercent(0.25)
                .warningThresholdRatio(0.80)
                .failureThresholdMultiplier(1.25)
                .pixelTolerance(32)
                .compareOnlyRegion(VisualRegion.of(500, 300, 700, 420))
                .ignoreRegion(VisualRegion.of(20, 20, 80, 40))
                .writeHtmlReport(true)
                .build()
);

if (!result.passed()) {
    throw new AssertionError(result.failureMessage());
}
```

### Configurazioni rapide

Usa questi overload quando vuoi restare sul caso standard senza costruire manualmente `VisualCompareOptions`.

| Caso d'uso | Metodo |
| --- | --- |
| Default completo | `compare(byte[], String)` |
| Pixel tolerance custom | `compare(byte[], String, int)` |
| Soglia percentuale custom | `compare(byte[], String, double)` |
| Pixel tolerance e soglia custom | `compare(byte[], String, int, double)` |
| Soglia custom e area limitata | `compare(byte[], String, double, VisualRegion)` |
| Pixel tolerance, soglia custom e area limitata | `compare(byte[], String, int, double, VisualRegion)` |

Default completo:

```java
VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary"
);
```

Il metodo usa questi default:

```text
pixelTolerance = 32
maxDiffPercent = 0.25
```

Con pixel tolerance custom e soglia default:

```java
VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary",
        24
);
```

Con soglia percentuale custom e pixel tolerance default:

```java
VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary",
        0.10
);
```

Il valore `0.10` significa `0.10%` di differenza massima accettata.

Con pixel tolerance e soglia percentuale custom:

```java
VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary",
        32,
        0.25
);
```

Con soglia custom e area limitata usando pixel tolerance default:

```java
VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary",
        0.25,
        VisualRegion.of(500, 300, 700, 420)
);
```

Con pixel tolerance, soglia custom e area limitata:

```java
VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary",
        32,
        0.25,
        VisualRegion.of(500, 300, 700, 420)
);
```

`VisualRegion.of(x, y, width, height)` usa coordinate pixel sull'immagine baseline.

### Assert nel test

Esempio tipico dentro un test E2E:

```java
VisualCompareResult result = VisualAssert.compare(
        screenshotBytes,
        "checkout-summary",
        32,
        0.25,
        VisualRegion.of(500, 300, 700, 420)
);

assertThat(result.passed())
        .as(result.failureMessage())
        .isTrue();
```

`passed()` torna `true` per `PASSED` e `WARNING`; torna `false` solo per `FAILED`.

Lo stato completo e' disponibile con:

```java
result.status();
```

## Baseline Save

Per salvare una baseline:

```java
import it.aruba.qaa.cv.VisualAssertUtil;

byte[] screenshotBytes = captureScreenshotBytes();
VisualAssertUtil.saveBaselineScreenshot(screenshotBytes, "access-page");
```

Il salvataggio predefinito usa:

```text
src/test/resources/test/cv_img
```

L'immagine viene poi letta dal classpath come:

```text
test/cv_img/access-page.png
```

## Opzioni

```java
VisualCompareOptions options = VisualCompareOptions.builder()
        .artifactName("access-page")
        .outputDirectory(Path.of("target", "visual-assert"))
        .maxDiffPercent(0.25)
        .maxDiffPixels(0)
        .warningThresholdRatio(0.80)
        .failureThresholdMultiplier(1.25)
        .pixelTolerance(32)
        .compareOnlyRegion(VisualRegion.of(500, 300, 700, 420))
        .ignoreRegion(VisualRegion.of(10, 10, 100, 50))
        .writeExpectedImage(true)
        .writeActualImage(true)
        .writeDiffImage(true)
        .writeHtmlReport(true)
        .build();
```

`compareOnlyRegion` limita il confronto a una sola area dell'immagine.

`ignoreRegion` esclude una o piu' aree dal confronto.

Se `compareOnlyRegion` e `ignoreRegion` sono usate insieme, le coordinate di `ignoreRegion` sono relative alla regione ritagliata.

`warningThresholdRatio` definisce quando iniziare a segnalare warning rispetto alla soglia. Con `0.80`, un valore sopra l'80% della soglia entra in warning.

`failureThresholdMultiplier` definisce quando il warning diventa fallimento. Con `1.25`, il test fallisce solo quando supera del 25% la soglia configurata.

## Output

Directory output predefinita:

```text
target/visual-assert
```

Artifact:

```text
<artifactName>-expected.png
<artifactName>-actual.png
<artifactName>-diff.png
<artifactName>-report.html
```

Il path del report e' disponibile dal risultato:

```java
result.htmlReport().ifPresent(path -> System.out.println("Visual report: " + path));
```

Il report HTML viene generato usando il template:

```text
src/main/resources/it/aruba/qaa/cv/visual-report-template.html
```
