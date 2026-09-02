# A worked example — a different catalogue, start to finish

Two components from an unrelated domain, integrated exactly as yours should be.

## 1. Read the manifests

```
$ unzip -p lib/sensors.jar META-INF/MANIFEST.MF
Name: com/vendor/sensors/SensorsBasic.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: temperature
Fluxtion-Constructor: ()

Name: com/vendor/sensors/SensorsPlus.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: temperature,humidity
Fluxtion-Constructor: ()

$ unzip -p lib/comfort.jar META-INF/MANIFEST.MF
Name: com/vendor/comfort/ComfortIndex.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: comfort
Fluxtion-Requires: TemperatureApi,HumidityApi
Fluxtion-Constructor: (TemperatureApi,HumidityApi)
```

## 2. Match against the requirement

*"Publish the comfort index."* `ComfortIndex` requires `HumidityApi`, which only `SensorsPlus`
provides — so `SensorsBasic` is out. **The requirement selects through the dependency, not just
through the figure list.**

## 3. Find the field names

```
$ javap -cp lib/sensors.jar com.vendor.sensors.SensorsPlus
public class com.vendor.sensors.SensorsPlus {
  public final com.vendor.sensors.Temperature temperature;
  public final com.vendor.sensors.Humidity humidity;
  public com.vendor.sensors.SensorsPlus();
  public com.vendor.sensors.SensorsPlus(Temperature, Humidity);   <- generator's, never yours
}
```

## 4. Write the bean file

```xml
<bean id="sensors" class="com.vendor.sensors.SensorsPlus"/>
<bean id="comfort" class="com.vendor.comfort.ComfortIndex">
    <constructor-arg value="#{sensors.temperature}"/>
    <constructor-arg value="#{sensors.humidity}"/></bean>
```

Two beans. `#{sensors.temperature}` reaches the node; `ref="sensors"` would reach the holder and the
graph would come out empty.

## 5. Build and run

```
mvn -q -o test
```

That is the whole integration. No Java was written for it beyond the runner.
