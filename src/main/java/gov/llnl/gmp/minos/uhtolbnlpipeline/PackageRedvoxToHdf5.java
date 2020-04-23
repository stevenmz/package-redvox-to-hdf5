/*
 * Copyright 2019 Lawrence Livermore National Security
 */
package gov.llnl.gmp.minos.uhtolbnlpipeline;

import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.object.Attribute;
import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.h5.H5Datatype;
import hdf.object.h5.H5File;
import io.redvox.api900.Reader;
import io.redvox.api900.WrappedRedvoxPacket;
import io.redvox.api900.sensors.AccelerometerSensor;
import io.redvox.api900.sensors.BarometerSensor;
import io.redvox.api900.sensors.GyroscopeSensor;
import io.redvox.api900.sensors.ImageSensor;
import io.redvox.api900.sensors.InfraredSensor;
import io.redvox.api900.sensors.LightSensor;
import io.redvox.api900.sensors.LocationSensor;
import io.redvox.api900.sensors.MagnetometerSensor;
import io.redvox.api900.sensors.MicrophoneSensor;
import io.redvox.api900.sensors.TimeSynchronizationSensor;
import io.redvox.apis.Api900;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.ArrayUtils;

/**
 * A program to convert a directory of Redvox JSON packets to a single HDF5
 * file.
 * 
 * @author Steven Magana-Zook (maganazook1@llnl.gov)
 */
public class PackageRedvoxToHdf5 {

    private static final Datatype FLOAT_TYPE = new H5Datatype(Datatype.CLASS_FLOAT, 4, Datatype.ORDER_BE, Datatype.NATIVE);
    private static final Datatype DOUBLE_TYPE = new H5Datatype(Datatype.CLASS_FLOAT, 8, Datatype.ORDER_BE, Datatype.NATIVE);
    private static final Datatype LONG_TYPE = new H5Datatype(Datatype.CLASS_INTEGER, 8, Datatype.ORDER_BE, Datatype.NATIVE);
    private static final Datatype BYTE_TYPE = new H5Datatype(Datatype.CLASS_INTEGER, 1, Datatype.ORDER_BE, Datatype.NATIVE);
    private static final Datatype INT_TYPE = new H5Datatype(Datatype.CLASS_INTEGER, 4, Datatype.ORDER_BE, Datatype.NATIVE);
    private static final Datatype STRING_TYPE = new H5Datatype(Datatype.CLASS_STRING, 1000, Datatype.NATIVE, Datatype.NATIVE);

    /**
     * The main runner method.
     * 
     * @param args
     *            Expected arguments: input directory, output file path.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.err.println("Usage: java -Djava.library.path=<path to hdf5 library> -jar PackageMavenToHdf5-1.0.0.jar <input directory> <output filename>");
            return;
        }

        String inputDirectory = args[0];
        String outputFilename = args[1];

        // Validate input parameters
        Path pathInputDirectory = Paths.get(inputDirectory);
        boolean doesPathExist = pathInputDirectory.toFile().exists();
        boolean isPathDirectory = pathInputDirectory.toFile().isDirectory();
        if (!doesPathExist || !isPathDirectory) {
            System.err.println("Invalid input directory provided.");
            return;
        }

        if (!outputFilename.endsWith(".h5")) {
            outputFilename += ".h5";
        }

        // Get a list of files to process
        Stream<Path> filesStream = Files.walk(pathInputDirectory);
        ArrayList<Path> filePaths = filesStream.filter((Path currentPath) -> currentPath.toFile().isFile())
                                               .filter((Path currentPath) -> currentPath.toString().endsWith(".json"))
                                               .collect(Collectors.toCollection(ArrayList::new));

        if (filePaths.size() <= 0) {
            System.err.println("There were no files to process.");
            return;
        }

        // Process the files into an HDF5 file.
        createHdf5File(outputFilename, filePaths);
    }

    /**
     * A method to create an HDF5 file out of a collection of JSON Serialized
     * Redvox packets.
     * 
     * @param strHdf5FilePath
     *            The path to where the HDF5 file should be created.
     * @param filePaths
     *            The collection of Redvox packets to add to the HDF5 file as
     *            datasets.
     * @return The populated HDF5 file.
     */
    private static H5File createHdf5File(String strHdf5FilePath, ArrayList<Path> filePaths) {
        H5File file = null;
        try {
            final Path pathHdf5File = Paths.get(strHdf5FilePath);

            if (Files.exists(pathHdf5File)) {
                System.out.println("An existing file was found at " + strHdf5FilePath + " it will be overwritten.");
                Files.delete(pathHdf5File);
            }

            file = new H5File(strHdf5FilePath, FileFormat.CREATE);
            file = (H5File) file.createFile(strHdf5FilePath, FileFormat.FILE_CREATE_OPEN);
            file.open();

            for (Path filePath : filePaths) {
                try {
                    addDataset(filePath, file);
                } catch (Exception e) {
                    System.err.println(e);
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(PackageRedvoxToHdf5.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (HDF5Exception ex) {
                    System.err.println(ex);
                }
            }
        }
        return file;
    }

    /**
     * A method to add a Redvox packet to an HDF5 file as a dataset.
     * 
     * @param filePath
     *            The path to the JSON Serialized Redvox packet on disk.
     * @param h5File
     *            The HDF5 file being modified.
     * @throws Exception
     */
    private static void addDataset(Path filePath, H5File h5File) throws Exception {
        String datasetName = filePath.getFileName().toString().replace(".json", "");

        Optional<Api900.RedvoxPacket> optionalPacket = ReadPacketJson(filePath);
        if (optionalPacket == null || optionalPacket.isPresent() == false) {
            throw new Exception("Could not read Redvox packet from file: " + filePath);
        }

        Api900.RedvoxPacket redvoxPacket = optionalPacket.get();
        WrappedRedvoxPacket wrappedPacket = new WrappedRedvoxPacket(redvoxPacket);

        Optional<MicrophoneSensor> optionalMicrophoneChannel = wrappedPacket.microphoneChannel();
        if (optionalMicrophoneChannel.isPresent() == false) {
            throw new Exception("Microphone sensor not present in file: " + filePath);
        }

        Group packetRootGroup = h5File.createGroup(datasetName, (Group) h5File.getRootObject());
        if (packetRootGroup == null) {
            throw new Exception("Could not create the packet group: " + filePath);
        }

        // Add metadata not specific to any sensor
        long[] genericMetadataDims = { 1 };
        Attribute attributeGenericMetadata = new Attribute("acquisitionServer", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.acquisitionServer() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("api", INT_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new int[] { wrappedPacket.api() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("appFileStartTimestampEpochMicrosecondsUtc", LONG_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new long[] { wrappedPacket.appFileStartTimestampEpochMicrosecondsUtc() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("appFileStartTimestampMachine", LONG_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new long[] { wrappedPacket.appFileStartTimestampMachine() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("appVersion", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.appVersion() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("batteryLevelPercent", FLOAT_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new float[] { wrappedPacket.batteryLevelPercent() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("deviceMake", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.deviceMake() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("deviceModel", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.deviceModel() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("deviceOs", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.deviceOs() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("deviceOsVersion", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.deviceOsVersion() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("deviceTemperatureC", FLOAT_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new float[] { wrappedPacket.deviceTemperatureC() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("redvoxId", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.redvoxId() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("serverTimestampEpochMicrosecondsUtc", LONG_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new long[] { wrappedPacket.serverTimestampEpochMicrosecondsUtc() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("timeSynchronizationServer", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.timeSynchronizationServer() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        attributeGenericMetadata = new Attribute("uuid", STRING_TYPE, genericMetadataDims);
        attributeGenericMetadata.setValue(new String[] { wrappedPacket.uuid() });
        h5File.writeAttribute(packetRootGroup, attributeGenericMetadata, false);

        int gzipCompressionLevel = 0;

        MicrophoneSensor microphoneSensor = optionalMicrophoneChannel.get();
        List<Long> microphoneData = microphoneSensor.payloadValues();
        long[] microphoneDataPrimitive = ArrayUtils.toPrimitive(microphoneData.toArray(new Long[0]));

        long[] dims = { 1, (long) microphoneDataPrimitive.length };
        long[] chunks = null;
        long[] maxdims = dims;
        Dataset datasetMicrophone = h5File.createScalarDS("microphone", packetRootGroup, LONG_TYPE, dims, maxdims, chunks, gzipCompressionLevel, microphoneDataPrimitive);

        if (datasetMicrophone == null) {
            throw new Exception("Could not create the microphone dataset: " + filePath);
        }

        // Add microphone metadata as attributes
        MetadataMapToAttribute(h5File, datasetMicrophone, microphoneSensor.metadataMap());

        Optional<AccelerometerSensor> optionalAccelerometerChannel = wrappedPacket.accelerometerChannel();
        if (optionalAccelerometerChannel.isPresent()) {
            AccelerometerSensor accelerometerSensor = optionalAccelerometerChannel.get();
            List<Double> accelerometerValues = accelerometerSensor.payloadValues();
            double[] accelerometerDataPrimitive = ArrayUtils.toPrimitive(accelerometerValues.toArray(new Double[0]));

            dims = new long[] { 1, (long) accelerometerDataPrimitive.length };
            maxdims = dims;
            Dataset datasetAccelerometer = h5File.createScalarDS("accelerometer", packetRootGroup, DOUBLE_TYPE, dims, maxdims, chunks, gzipCompressionLevel, accelerometerDataPrimitive);

            MetadataMapToAttribute(h5File, datasetAccelerometer, accelerometerSensor.metadataMap());
        }

        Optional<BarometerSensor> optionalBarometerChannel = wrappedPacket.barometerChannel();
        if (optionalBarometerChannel.isPresent()) {
            BarometerSensor barometerSensor = optionalBarometerChannel.get();
            List<Double> barometerValues = barometerSensor.payloadValues();
            double[] barometerDataPrimitive = ArrayUtils.toPrimitive(barometerValues.toArray(new Double[0]));

            dims = new long[] { 1, (long) barometerDataPrimitive.length };
            maxdims = dims;
            Dataset datasetBarometer = h5File.createScalarDS("barometer", packetRootGroup, DOUBLE_TYPE, dims, maxdims, chunks, gzipCompressionLevel, barometerDataPrimitive);

            MetadataMapToAttribute(h5File, datasetBarometer, barometerSensor.metadataMap());
        }

        Optional<GyroscopeSensor> optionalGyroscopeChannel = wrappedPacket.gyroscopeChannel();
        if (optionalGyroscopeChannel.isPresent()) {
            GyroscopeSensor gyroscopeSensor = optionalGyroscopeChannel.get();

            List<Double> gyroscopeValuesX = gyroscopeSensor.payloadValuesX();
            List<Double> gyroscopeValuesY = gyroscopeSensor.payloadValuesY();
            List<Double> gyroscopeValuesZ = gyroscopeSensor.payloadValuesZ();
            double[] gyroscopeDataPrimitiveX = ArrayUtils.toPrimitive(gyroscopeValuesX.toArray(new Double[0]));
            double[] gyroscopeDataPrimitiveY = ArrayUtils.toPrimitive(gyroscopeValuesY.toArray(new Double[0]));
            double[] gyroscopeDataPrimitiveZ = ArrayUtils.toPrimitive(gyroscopeValuesZ.toArray(new Double[0]));

            Group groupGyroscope = h5File.createGroup("gyroscope", packetRootGroup);
            MetadataMapToAttribute(h5File, groupGyroscope, gyroscopeSensor.metadataMap());

            long[] dimsX = new long[] { 1, (long) gyroscopeDataPrimitiveX.length };
            long[] maxdimsX = dimsX;
            h5File.createScalarDS("X", groupGyroscope, DOUBLE_TYPE, dimsX, maxdimsX, chunks, gzipCompressionLevel, gyroscopeDataPrimitiveX);

            long[] dimsY = new long[] { 1, (long) gyroscopeDataPrimitiveY.length };
            long[] maxdimsY = dimsY;
            h5File.createScalarDS("Y", groupGyroscope, DOUBLE_TYPE, dimsY, maxdimsY, chunks, gzipCompressionLevel, gyroscopeDataPrimitiveX);

            long[] dimsZ = new long[] { 1, (long) gyroscopeDataPrimitiveZ.length };
            long[] maxdimsZ = dimsZ;
            h5File.createScalarDS("Z", groupGyroscope, DOUBLE_TYPE, dimsZ, maxdimsZ, chunks, gzipCompressionLevel, gyroscopeDataPrimitiveX);
        }

        Optional<ImageSensor> optionalImageChannel = wrappedPacket.imageChannel();
        if (optionalImageChannel.isPresent()) {
            ImageSensor imageSensor = optionalImageChannel.get();
            byte[] imageDataPrimitive = imageSensor.payloadBytes();

            dims = new long[] { 1, (long) imageDataPrimitive.length };
            maxdims = dims;
            Dataset datasetImage = h5File.createScalarDS("image", packetRootGroup, BYTE_TYPE, dims, maxdims, chunks, gzipCompressionLevel, imageDataPrimitive);

            MetadataMapToAttribute(h5File, datasetImage, imageSensor.metadataMap());
        }

        Optional<InfraredSensor> optionalInfraredChannel = wrappedPacket.infraredChannel();
        if (optionalInfraredChannel.isPresent()) {
            InfraredSensor infraredSensor = optionalInfraredChannel.get();
            List<Double> infraredValues = infraredSensor.payloadValues();
            double[] infraredDataPrimitive = ArrayUtils.toPrimitive(infraredValues.toArray(new Double[0]));

            dims = new long[] { 1, (long) infraredDataPrimitive.length };
            maxdims = dims;
            Dataset datasetInfrared = h5File.createScalarDS("infrared", packetRootGroup, DOUBLE_TYPE, dims, maxdims, chunks, gzipCompressionLevel, infraredDataPrimitive);
            MetadataMapToAttribute(h5File, datasetInfrared, infraredSensor.metadataMap());
        }

        Optional<LightSensor> optionalLightChannel = wrappedPacket.lightChannel();
        if (optionalLightChannel.isPresent()) {
            LightSensor lightSensor = optionalLightChannel.get();
            List<Double> lightValues = lightSensor.payloadValues();
            double[] lightDataPrimitive = ArrayUtils.toPrimitive(lightValues.toArray(new Double[0]));

            dims = new long[] { 1, (long) lightDataPrimitive.length };
            maxdims = dims;
            Dataset datasetLight = h5File.createScalarDS("light", packetRootGroup, DOUBLE_TYPE, dims, maxdims, chunks, gzipCompressionLevel, lightDataPrimitive);
            MetadataMapToAttribute(h5File, datasetLight, lightSensor.metadataMap());
        }

        Optional<LocationSensor> optionalLocationChannel = wrappedPacket.locationChannel();
        if (optionalLocationChannel.isPresent()) {
            LocationSensor locationSensor = optionalLocationChannel.get();

            Group groupLocation = h5File.createGroup("location", packetRootGroup);
            MetadataMapToAttribute(h5File, groupLocation, locationSensor.metadataMap());

            List<Double> locationValuesAccuracy = locationSensor.payloadValuesAccuracy();
            List<Double> locationValuesAltitude = locationSensor.payloadValuesAltitude();
            List<Double> locationValuesLatitude = locationSensor.payloadValuesLatitude();
            List<Double> locationValuesLongitude = locationSensor.payloadValuesLongitude();
            double[] locationDataPrimitiveAccuracy = ArrayUtils.toPrimitive(locationValuesAccuracy.toArray(new Double[0]));
            double[] locationDataPrimitiveAltitude = ArrayUtils.toPrimitive(locationValuesAltitude.toArray(new Double[0]));
            double[] locationDataPrimitiveLatitude = ArrayUtils.toPrimitive(locationValuesLatitude.toArray(new Double[0]));
            double[] locationDataPrimitiveLongitude = ArrayUtils.toPrimitive(locationValuesLongitude.toArray(new Double[0]));

            long[] dimsAccuracy = new long[] { 1, (long) locationDataPrimitiveAccuracy.length };
            h5File.createScalarDS("accuracy", groupLocation, DOUBLE_TYPE, dimsAccuracy, dimsAccuracy, chunks, gzipCompressionLevel, locationDataPrimitiveAccuracy);

            long[] dimsAltitude = new long[] { 1, (long) locationDataPrimitiveAltitude.length };
            h5File.createScalarDS("altitude", groupLocation, DOUBLE_TYPE, dimsAltitude, dimsAltitude, chunks, gzipCompressionLevel, locationDataPrimitiveAltitude);

            long[] dimsLatitude = new long[] { 1, (long) locationDataPrimitiveLatitude.length };
            h5File.createScalarDS("latitude", groupLocation, DOUBLE_TYPE, dimsLatitude, dimsLatitude, chunks, gzipCompressionLevel, locationDataPrimitiveLatitude);

            long[] dimsLongitude = new long[] { 1, (long) locationDataPrimitiveLongitude.length };
            h5File.createScalarDS("longitude", groupLocation, DOUBLE_TYPE, dimsLongitude, dimsLongitude, chunks, gzipCompressionLevel, locationDataPrimitiveLongitude);
        }

        Optional<MagnetometerSensor> optionaMagnetometerChannel = wrappedPacket.magnetometerChannel();
        if (optionaMagnetometerChannel.isPresent()) {
            MagnetometerSensor magnetometerSensor = optionaMagnetometerChannel.get();
            List<Double> magnetometerValues = magnetometerSensor.payloadValues();
            double[] magnetometerDataPrimitive = ArrayUtils.toPrimitive(magnetometerValues.toArray(new Double[0]));

            dims = new long[] { 1, (long) magnetometerDataPrimitive.length };
            maxdims = dims;
            Dataset datasetMagnetometer = h5File.createScalarDS("magnetometer", packetRootGroup, DOUBLE_TYPE, dims, maxdims, chunks, gzipCompressionLevel, magnetometerDataPrimitive);
            MetadataMapToAttribute(h5File, datasetMagnetometer, magnetometerSensor.metadataMap());
        }

        Optional<TimeSynchronizationSensor> optionaTimeSynchronizationChannel = wrappedPacket.timeSynchronizationChannel();
        if (optionaTimeSynchronizationChannel.isPresent()) {
            TimeSynchronizationSensor timeSynchronizationSensor = optionaTimeSynchronizationChannel.get();
            List<Long> timeSynchronizationValues = timeSynchronizationSensor.payloadValues();
            long[] timeSynchronizationDataPrimitive = ArrayUtils.toPrimitive(timeSynchronizationValues.toArray(new Long[0]));

            dims = new long[] { 1, (long) timeSynchronizationDataPrimitive.length };
            maxdims = dims;
            Dataset datasetTimeSynchronization = h5File.createScalarDS(
                    "timeSynchronization",
                        packetRootGroup,
                        LONG_TYPE,
                        dims,
                        maxdims,
                        chunks,
                        gzipCompressionLevel,
                        timeSynchronizationDataPrimitive);
            MetadataMapToAttribute(h5File, datasetTimeSynchronization, timeSynchronizationSensor.metadataMap());
        }
    }

    /**
     * A method to add all of the Map entries as Attributes to some HObject.
     * 
     * @param h5File
     *            The file being moodified.
     * @param hObject
     *            The HDF Object (Group, Dataset, etc.) that the attribute will
     *            be written to.
     * @param metadata
     *            A Map of String pairs containing the metadata to add as
     *            attributes.
     * @throws HDF5Exception
     */
    private static void MetadataMapToAttribute(H5File h5File, HObject hObject, Map<String, String> metadata) throws HDF5Exception {
        long[] attrDims = { 1 };
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            Datatype stringType = new H5Datatype(Datatype.CLASS_STRING, value.length(), Datatype.NATIVE, Datatype.NATIVE);

            Attribute attribute = new Attribute(key, stringType, attrDims);
            boolean attrExisted = false;
            attribute.setValue(new String[] { value });
            h5File.writeAttribute(hObject, attribute, attrExisted);
        }
    }

    /**
     * A method to read a Redvox packet from a JSON file.
     * 
     * @param filePath
     *            THe path to the JSON Serialized packet on disk.
     * @return An optional Redvox packet, or null on error.
     */
    private static Optional<Api900.RedvoxPacket> ReadPacketJson(Path filePath) {
        try {
            List<String> allLines = Files.readAllLines(filePath);
            StringBuilder appendedLines = new StringBuilder();
            allLines.forEach((line) -> {
                appendedLines.append(line.replace("\\n", "").replace("\\", ""));
            });

            String json = appendedLines.toString();
            if (json.startsWith("\"")) {
                json = json.replaceFirst("\"", "");
            }
            if (json.endsWith("\"")) {
                json = json.substring(0, json.length() - 1);
            }

            return Reader.readJson(json);
        } catch (IOException ex) {
            System.err.println(ex);
            return null;
        }
    }

}
