package de.mas.jnus.lib.entities.fst;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.mas.jnus.lib.entities.content.Content;
import de.mas.jnus.lib.entities.content.ContentFSTInfo;
import de.mas.jnus.lib.utils.ByteUtils;
import lombok.extern.java.Log;

@Log
public class FSTService {
    protected static void parseFST(FSTEntry rootEntry, byte[] fstSection, byte[] namesSection,Map<Integer,Content> contentsByIndex,Map<Integer,ContentFSTInfo> contentsFSTByIndex) {      
        int totalEntries = ByteUtils.getIntFromBytes(fstSection, 0x08);

        int level = 0;
        int[] LEntry = new int[16];
        int[] Entry = new int[16];
                
        HashMap<Integer,FSTEntry> fstEntryToOffsetMap = new HashMap<>();
        
        rootEntry.setDir(true);        
        Entry[level] = 0;
        LEntry[level++] =  0;
        
        fstEntryToOffsetMap.put(0,rootEntry);   
        
        for(int i = 1;i< totalEntries;i++){
            int entryOffset = i;
            if( level > 0){
                while( LEntry[level-1] == i ){level--;}
            }
            
            byte[] curEntry = Arrays.copyOfRange(fstSection,i*0x10,(i+1)*0x10);

            FSTEntry entry = new FSTEntry();
            
            String path = getFullPath(level, fstSection, namesSection, Entry);
            String filename = getName(curEntry,namesSection);
            
            long fileOffset = ByteUtils.getIntFromBytes(curEntry, 0x04);
            long fileSize = ByteUtils.getUnsingedIntFromBytes(curEntry, 0x08);
          
            short flags = ByteUtils.getShortFromBytes(curEntry, 0x0C);
            short contentIndex = ByteUtils.getShortFromBytes(curEntry, 0x0E);
                                  
            if((curEntry[0] & FSTEntry.FSTEntry_notInNUS) == FSTEntry.FSTEntry_notInNUS){
                entry.setNotInPackage(true);
            }
                        
            if((curEntry[0] & FSTEntry.FSTEntry_DIR) == FSTEntry.FSTEntry_DIR){
                entry.setDir(true);
                int parentOffset = (int) fileOffset;
                int nextOffset = (int) fileSize;
                                
                FSTEntry parent = fstEntryToOffsetMap.get(parentOffset); 
                if(parent != null){
                    log.fine("no parent found for a FSTEntry");
                    parent.addChildren(entry);
                }
                
                Entry[level] = i;
                LEntry[level++] =  nextOffset ;
               
                if( level > 15 ){
                    log.warning("level > 15");
                    break;
                }
            }else{
                entry.setFileOffset(fileOffset<<5);               
                entry.setFileSize(fileSize);
                FSTEntry parent = fstEntryToOffsetMap.get(Entry[level-1]);                
                if(parent != null){
                    parent.addChildren(entry);
                }else{
                    log.warning(entryOffset +"couldn't find parent @ " + Entry[level-1]);
                }           
            }     
            
            entry.setFlags(flags);
            entry.setFilename(filename);
            entry.setPath(path);
          
            if(contentsByIndex != null){
                Content content = contentsByIndex.get((int)contentIndex);
                if(content == null){
                    log.warning("Content for FST Entry not found");
                }else{
                    entry.setContent(content);
                    
                    ContentFSTInfo contentFSTInfo = contentsFSTByIndex.get((int)contentIndex);
                    if(contentFSTInfo == null){
                        log.warning("ContentFSTInfo for FST Entry not found");
                    }else{
                        content.setContentFSTInfo(contentFSTInfo);
                    }
                }
            }

            entry.setContentFSTID(contentIndex);
                        
            fstEntryToOffsetMap.put(entryOffset, entry);
        }
    }
        
    private static int getNameOffset(byte[] curEntry) {
        //Its a 24bit number. We overwrite the first byte, then we can read it as an Integer.
        //But at first we make a copy.
        byte[] entryData =  Arrays.copyOf(curEntry, curEntry.length);
        entryData[0] = 0;
        return ByteUtils.getIntFromBytes(entryData, 0);
    }

    public static  String getName(byte[] data, byte[] namesSection){
        int nameOffset = getNameOffset(data);
        
        int j = 0;

        while(namesSection[nameOffset + j] != 0){j++;}
        return(new String(Arrays.copyOfRange(namesSection,nameOffset, nameOffset + j)));
    }
    
    
    public static  String getFullPath(int level,byte[] fstSection,byte[] namesSection, int[] Entry){
        StringBuilder sb = new StringBuilder();             
        for(int i=0; i < level; i++){
            int entryOffset = Entry[i]*0x10;
            byte[] entryData =  Arrays.copyOfRange(fstSection, entryOffset,entryOffset + 10);
            String entryName = getName(entryData,namesSection);
           
            sb.append(entryName).append(File.separator);     
        }
        return sb.toString();
    }
}
