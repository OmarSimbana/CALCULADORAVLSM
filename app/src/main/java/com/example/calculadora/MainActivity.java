package com.example.calculadora;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ViewGroup;
import android.view.View;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    EditText etIp, etPrefix, etSubnets;
    LinearLayout hostsContainer;
    LinearLayout containerResults;
    Button btnCalculate;
    ArrayList<EditText> hostInputs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etIp = findViewById(R.id.etIp);
        etPrefix = findViewById(R.id.etPrefix);
        etSubnets = findViewById(R.id.etSubnets);
        hostsContainer = findViewById(R.id.hostsContainer);
        btnCalculate = findViewById(R.id.btnCalculate);
        containerResults = findViewById(R.id.containerResults);

        etSubnets.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                generarCamposHost(s.toString());
            }
        });

        btnCalculate.setOnClickListener(v -> calcularVLSM());
    }

    private void generarCamposHost(String texto) {
        hostsContainer.removeAllViews();
        hostInputs.clear();

        if (texto.isEmpty()) return;

        try {
            int cantidad = Integer.parseInt(texto);
            for (int i = 0; i < cantidad; i++) {
                EditText input = new EditText(this);
                input.setHint("Hosts para subred " + (i + 1));
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                hostsContainer.addView(input);
                hostInputs.add(input);
            }
        } catch (NumberFormatException ignored) {}
    }

    private void calcularVLSM() {
        containerResults.removeAllViews();

        String ip = etIp.getText().toString().trim();
        int prefix = Integer.parseInt(etPrefix.getText().toString().trim());
        int totalBitsDisponibles = 32 - prefix;

        ArrayList<Integer> hosts = new ArrayList<>();

        for (EditText campo : hostInputs) {
            String valor = campo.getText().toString().trim();
            if (valor.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos de host", Toast.LENGTH_SHORT).show();
                return;
            }
            hosts.add(Integer.parseInt(valor));
        }

        int subredesSolicitadas = hosts.size();
        int subredesPosibles = (int) Math.pow(2, totalBitsDisponibles);
        if (subredesSolicitadas > subredesPosibles) {
            Toast.makeText(this, "No se pueden crear " + subredesSolicitadas + " subredes con /" + prefix, Toast.LENGTH_LONG).show();
            return;
        }

        // Crear subredes sin nombre aún
        ArrayList<Subnet> subredes = new ArrayList<>();
        for (int i = 0; i < hosts.size(); i++) {
            subredes.add(new Subnet(hosts.get(i)));
        }

        // Ordenar por mayor número de hosts
        Collections.sort(subredes, (a, b) -> b.hosts - a.hosts);

        // Alinear IP base al inicio de subred según el prefijo
        long ipOriginal = ipToLong(ip);
        long mask = maskFromPrefix(prefix);
        long ipBase = ipOriginal & mask;

        ArrayList<ResultadoSubred> resultados = new ArrayList<>();

        for (int i = 0; i < subredes.size(); i++) {
            Subnet subred = subredes.get(i);

            int bits = (int) Math.ceil(Math.log(subred.hosts + 2) / Math.log(2));
            int nuevaMascara = 32 - bits;
            long blockSize = (long) Math.pow(2, bits);

            long ipRed = ipBase;
            long primerHost = ipRed + 1;
            long ultimoHost = ipRed + blockSize - 2;
            long broadcast = ipRed + blockSize - 1;

            String nombre = "SUB-" + (i + 1); // nombre por orden de asignación

            resultados.add(new ResultadoSubred(nombre, subred.hosts, ipRed, nuevaMascara, primerHost, ultimoHost, broadcast));
            ipBase += blockSize;
        }

        // Mostrar resultados en el orden de asignación (ya están en orden)
        for (ResultadoSubred r : resultados) {
            mostrarResultado(r.nombre, r.hosts, r.ipRed, r.prefix, r.primerHost, r.ultimoHost, r.broadcast);
        }
    }

    private void mostrarResultado(String nombre, int hosts, long ipRed, int prefix, long primerHost, long ultimoHost, long broadcast) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundColor(Color.parseColor("#FFFFFF"));
        card.setElevation(4);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, 32);
        card.setLayoutParams(lp);

        card.addView(item("Nombre", nombre));
        card.addView(item("Host Requeridos", String.valueOf(hosts)));
        card.addView(item("Dirección de Subred", longToIp(ipRed)));
        card.addView(item("Máscara de Subred", longToIp(maskFromPrefix(prefix))));
        card.addView(item("Prefijo", "/" + prefix));
        card.addView(item("Rango asignable", longToIp(primerHost) + " - " + longToIp(ultimoHost)));
        card.addView(item("Broadcast", longToIp(broadcast)));

        containerResults.addView(card);
    }

    private TextView item(String etiqueta, String valor) {
        TextView tv = new TextView(this);
        tv.setText(String.format(Locale.US, "%s: %s", etiqueta, valor));
        tv.setTextSize(14);
        tv.setPadding(0, 8, 0, 8);
        tv.setTextColor(Color.DKGRAY);
        return tv;
    }

    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (Long.parseLong(octets[i]) << (24 - 8 * i));
        }
        return result;
    }

    private String longToIp(long ip) {
        return String.format(Locale.US, "%d.%d.%d.%d",
                (ip >> 24) & 0xFF,
                (ip >> 16) & 0xFF,
                (ip >> 8) & 0xFF,
                ip & 0xFF);
    }

    private long maskFromPrefix(int prefix) {
        return prefix == 0 ? 0 : 0xFFFFFFFFL << (32 - prefix);
    }

    static class Subnet {
        int hosts;

        Subnet(int hosts) {
            this.hosts = hosts;
        }
    }

    static class ResultadoSubred {
        String nombre;
        int hosts;
        long ipRed;
        int prefix;
        long primerHost;
        long ultimoHost;
        long broadcast;

        ResultadoSubred(String nombre, int hosts, long ipRed, int prefix, long primerHost, long ultimoHost, long broadcast) {
            this.nombre = nombre;
            this.hosts = hosts;
            this.ipRed = ipRed;
            this.prefix = prefix;
            this.primerHost = primerHost;
            this.ultimoHost = ultimoHost;
            this.broadcast = broadcast;
        }
    }
}
