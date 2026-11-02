package dev.moonwitch.moondump;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Validate hactool-recognized keys locally; never disclose their values. */
public final class KeyNormalizer {
    private KeyNormalizer() {}

    public static final class Summary {
        public final int recognized;
        public final int ignored;
        Summary(int recognized, int ignored) { this.recognized=recognized; this.ignored=ignored; }
    }

    private static int expectedLength(String name) {
        switch (name) {
            case "header_key": case "header_key_source":
            case "sd_card_nca_key_source": case "sd_card_save_key_source":
                return 64;
            case "aes_kek_generation_source": case "aes_key_generation_source":
            case "key_area_key_application_source": case "key_area_key_ocean_source":
            case "key_area_key_system_source": case "titlekek_source":
            case "header_kek_source": case "package2_key_source":
            case "per_console_key_source": case "xci_header_key":
            case "sd_card_kek_source": case "save_mac_kek_source":
            case "save_mac_key_source": case "master_key_source":
            case "keyblob_mac_key_source": case "secure_boot_key":
            case "tsec_key": case "mariko_kek": case "mariko_bek":
            case "tsec_root_kek": case "package1_mac_kek": case "package1_kek":
            case "xci_t1_titlekey_kek_00": return 32;
            default: break;
        }
        if (!name.matches("[a-z0-9_]+_[0-9a-f]{2}")) return -1;
        int at = name.lastIndexOf('_');
        int index = Integer.parseInt(name.substring(at+1),16);
        String prefix = name.substring(0,at);
        if(prefix.equals("mariko_aes_class_key")) return index < 12 ? 32 : -1;
        if(index>=32) return -1;
        switch(prefix) {
            case "encrypted_keyblob": return 352;
            case "keyblob": return 288;
            case "keyblob_key_source": case "keyblob_key": case "keyblob_mac_key":
            case "mariko_master_kek_source": case "tsec_auth_signature":
            case "tsec_root_key": case "master_kek_source":
            case "package1_mac_key": case "master_kek":
            case "master_key": case "package1_key": case "package2_key":
            case "titlekek": case "key_area_key_application":
            case "key_area_key_ocean": case "key_area_key_system": return 32;
            default: return -1;
        }
    }

    public static Summary prepare(File original, File output) throws IOException {
        LinkedHashMap<String,String> keys = new LinkedHashMap<>();
        int ignored=0;
        try(BufferedReader input=Files.newBufferedReader(original.toPath(),StandardCharsets.UTF_8)) {
            String line; int lineNo=0;
            while((line=input.readLine())!=null) {
                lineNo++;
                String trimmed=line.replace("\uFEFF", "").trim();
                if(trimmed.isEmpty()||trimmed.startsWith("#")||trimmed.startsWith(";")) continue;
                int equals=trimmed.indexOf('=');
                if(equals<=0) {ignored++;continue;}
                String name=trimmed.substring(0,equals).trim().toLowerCase(Locale.ROOT);
                int expected=expectedLength(name);
                if(expected<0) {ignored++;continue;}
                String val=trimmed.substring(equals+1).trim();
                int comment=val.indexOf('#');if(comment>=0)val=val.substring(0,comment).trim();
                comment=val.indexOf(';');if(comment>=0)val=val.substring(0,comment).trim();
                if(val.startsWith("0x")||val.startsWith("0X")) val=val.substring(2);
                if(val.length()!=expected || !val.matches("(?i)[0-9a-f]+")) {
                    throw new IOException("prod.keys: entrada '"+name+"' na linha "+lineNo+
                        " esta mal formatada (esperado: "+expected+" caracteres hexadecimais; encontrado: "+val.length()+"). "+
                        "Confira essa linha no arquivo original ou extraia um novo prod.keys. Nao compartilhe os valores.");
                }
                keys.put(name,val.toLowerCase(Locale.ROOT));
            }
        }
        if(keys.isEmpty())throw new IOException("prod.keys nao tem chaves compativeis com o Hactool; confira o arquivo selecionado.");
        if(!keys.containsKey("header_key")&&!keys.containsKey("header_key_source"))
            throw new IOException("prod.keys nao contem header_key ou header_key_source; confira o dump de chaves.");
        try(BufferedWriter outputWriter=Files.newBufferedWriter(output.toPath(),StandardCharsets.UTF_8)) {
            for(Map.Entry<String,String> k:keys.entrySet()) {
                outputWriter.write(k.getKey());outputWriter.write(" = ");outputWriter.write(k.getValue());outputWriter.newLine();
            }
        }
        return new Summary(keys.size(), ignored);
    }
}
