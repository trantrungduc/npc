package org.d;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class CesarCode
{

    public CesarCode()
    {
    }

    public static String byteArrayToString(byte input[])
        throws UnsupportedEncodingException
    {
        if(input != null)
            return new String(input, "ISO8859_1");
        else
            return null;
    }

    public static byte[] stringToByteArray(String input)
        throws UnsupportedEncodingException
    {
        if(input != null)
            return input.getBytes("ISO8859_1");
        else
            return null;
    }

    public static String encode(String s)
    {
        try
        {
            byte data[] = stringToByteArray(s);
            int len = data.length;
            StringBuffer ret = new StringBuffer((len / 3 + 1) * 4);
            for(int i = 0; i < len; i++)
            {
                int c = data[i] >> 2 & 0x3f;
                ret.append(cvt.charAt(c));
                c = data[i] << 4 & 0x3f;
                if(++i < len)
                    c |= data[i] >> 4 & 0xf;
                ret.append(cvt.charAt(c));
                if(i < len)
                {
                    c = data[i] << 2 & 0x3f;
                    if(++i < len)
                        c |= data[i] >> 6 & 3;
                    ret.append(cvt.charAt(c));
                } else
                {
                    i++;
                    ret.append((char)fillchar1);
                }
                if(i < len)
                {
                    c = data[i] & 0x3f;
                    ret.append(cvt.charAt(c));
                } else
                {
                    ret.append((char)fillchar2);
                }
            }

            return ret.toString();
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public static String decode(String s)
    {
        try
        {
            byte data[] = stringToByteArray(s);
            int len = data.length;
            StringBuffer ret = new StringBuffer((len * 3) / 4);
            for(int i = 0; i < len; i++)
            {
                int c = cvt.indexOf(data[i]);
                i++;
                int c1 = cvt.indexOf(data[i]);
                c = c << 2 | c1 >> 4 & 3;
                ret.append((char)c);
                if(++i < len)
                {
                    c = data[i];
                    if(fillchar1 == c)
                        break;
                    c = cvt.indexOf((char)c);
                    c1 = c1 << 4 & 0xf0 | c >> 2 & 0xf;
                    ret.append((char)c1);
                }
                if(++i >= len)
                    continue;
                c1 = data[i];
                if(fillchar2 == c1)
                    break;
                c1 = cvt.indexOf((char)c1);
                c = c << 6 & 0xc0 | c1;
                ret.append((char)c);
            }

            return ret.toString();
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public static void main(String args[]) throws IOException, KeyManagementException, NoSuchAlgorithmException
    {
    	System.out.println(Mnp.gets("https://api.v-point.vn/if0510.do?NINSYO_ID=0912034888&NINSYO_UMU_FLG=0"));
    }

    public static String cvt = "opqrstEFGHIJKLMNOPQRSAB3456CDTUVWXYZabcdefghijklmnuvwxyz012789-_";
    private static int fillchar1 = 46;
    private static int fillchar2 = 46;

}
