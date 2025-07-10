package io.mosip.certify.api.service;

import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.Fill;
import org.geotools.styling.Stroke;
import org.geotools.styling.Style;
import org.geotools.styling.StyleBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Base64;

@Service
public class MapImageGeneratorServiceImpl implements MapImageGeneratorService {
    @Override
    public String generateMapImage(String multiDimensionalCoOrdinateArray) throws Exception {
        MapContent map = null;
        Graphics2D g = null;

        try {
            // Read WKT
            Geometry geom = new WKTReader().read(multiDimensionalCoOrdinateArray);

            // Build feature
            SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
            tb.setName("Area");
            tb.setCRS(DefaultGeographicCRS.WGS84);
            tb.add("the_geom", geom.getClass());
            SimpleFeatureType ft = tb.buildFeatureType();
            SimpleFeatureBuilder fb = new SimpleFeatureBuilder(ft);
            fb.add(geom);
            SimpleFeature feature = fb.buildFeature(null);

            DefaultFeatureCollection features = new DefaultFeatureCollection();
            features.add(feature);

            // Transform bounds
            ReferencedEnvelope world = features.getBounds();
            CoordinateReferenceSystem merc = CRS.decode("EPSG:3857", true);
            ReferencedEnvelope mercEnv = world.transform(merc, true);
            double padX = mercEnv.getWidth() * 0.1;
            double padY = mercEnv.getHeight() * 0.1;
            mercEnv.expandBy(padX, padY);

            // Map content setup
            map = new MapContent();
            map.getViewport().setCoordinateReferenceSystem(merc);
            map.getViewport().setBounds(mercEnv);

            // WMS tile setup
            String layerName = "s2cloudless-2024_3857";
            int width = 800, height = 400;
            String bbox = mercEnv.getMinX() + "," + mercEnv.getMinY() + "," + mercEnv.getMaxX() + "," + mercEnv.getMaxY();
            String wmsBase = "https://tiles.maps.eox.at/wms";
            String urlStr = wmsBase
                    + "?SERVICE=WMS"
                    + "&REQUEST=GetMap"
                    + "&VERSION=1.1.1"
                    + "&LAYERS=" + layerName
                    + "&STYLES="
                    + "&SRS=EPSG:3857"
                    + "&BBOX=" + bbox
                    + "&WIDTH=" + width
                    + "&HEIGHT=" + height
                    + "&FORMAT=image/png"
                    + "&TRANSPARENT=TRUE"
                    + "&FORMAT_OPTIONS=dpi:300";

            BufferedImage satImg = ImageIO.read(new URL(urlStr));

            // Draw combined image
            BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            g = combined.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(satImg, 0, 0, width, height, null);

            // Style polygon
            StyleBuilder sb = new StyleBuilder();
            Stroke stroke = sb.createStroke(Color.YELLOW, 2.0f, new float[]{10f, 5f});
            Fill translucentFill = sb.createFill(Color.YELLOW, 0.1);
            Style style = sb.createStyle(sb.createPolygonSymbolizer(stroke, translucentFill));
            map.addLayer(new FeatureLayer(features, style));

            // Render overlay
            StreamingRenderer renderer = new StreamingRenderer();
            renderer.setMapContent(map);
            renderer.paint(g, new Rectangle(0, 0, width, height), mercEnv);

            // Write image to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(combined, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);

        } catch (Exception e) {
            throw new RuntimeException("Error generating map image: " + e.getMessage(), e);
        } finally {
            if (g != null) g.dispose();
            if (map != null) map.dispose();
        }
    }
}
