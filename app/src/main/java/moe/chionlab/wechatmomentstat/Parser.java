package moe.chionlab.wechatmomentstat;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.chionlab.wechatmomentstat.Model.SnsInfo;

/**
 * Created by chiontang on 2/11/16.
 */
public class Parser {

    protected Class SnsDetailParser = null;
    protected Class SnsDetail = null;
    protected Class SnsObject = null;
    protected Class ModelClass = null;
    protected Class JpegObject = null;
    protected Class sfsObject = null;
    protected boolean inited = false;

    public Parser(Class SnsDetail, Class SnsDetailParser, Class SnsObject, Class ModelClass,
                  Class JpegObject, Class sfsObject) {
        this.SnsDetailParser = SnsDetailParser;
        this.SnsDetail = SnsDetail;
        this.SnsObject = SnsObject;
        this.ModelClass = ModelClass;
        this.JpegObject = JpegObject;
        this.sfsObject = sfsObject;
        inited = false;
    }

    public SnsInfo parseSnsAllFromBin(byte[] snsDetailBin, byte[] snsObjectBin) throws Throwable {
        Object snsDetail = parseSnsDetailFromBin(snsDetailBin);
        Object snsObject = parseSnsObjectFromBin(snsObjectBin);

        SnsInfo snsInfo = parseSnsDetail(snsDetail);
        parseSnsObject(snsObject, snsInfo);

        return snsInfo;
    }

    public Object parseSnsDetailFromBin(byte[] bin) throws Throwable {
        Object snsDetail = SnsDetail.newInstance();

        Method fromBinMethod = SnsDetail.getMethod(Config.SNS_DETAIL_FROM_BIN_METHOD, byte[].class);
        fromBinMethod.invoke(snsDetail, bin);
        return snsDetail;
    }

    public SnsInfo parseSnsDetail(Object snsDetail) throws Throwable {
        Method snsDetailParserMethod = SnsDetailParser.getMethod(Config.SNS_XML_GENERATOR_METHOD, SnsDetail);
        String xmlResult = (String) snsDetailParserMethod.invoke(this, snsDetail);
        return parseTimelineXML(xmlResult);
    }

    public Object parseSnsObjectFromBin(byte[] bin) throws Throwable {
        Object snsObject = SnsObject.newInstance();
        Method fromBinMethod = SnsObject.getMethod(Config.SNS_OBJECT_FROM_BIN_METHOD, byte[].class);
        fromBinMethod.invoke(snsObject, bin);
        return snsObject;
    }

    public SnsInfo parseTimelineXML(String xmlResult) throws Throwable {
        SnsInfo currentSns = new SnsInfo();
        Pattern userIdPattern = Pattern.compile("<username><!\\[CDATA\\[(.+?)\\]\\]></username>", Pattern.DOTALL);
        Pattern contentPattern = Pattern.compile("<contentDesc><!\\[CDATA\\[(.+?)\\]\\]></contentDesc>", Pattern.DOTALL);
        Pattern mediaTokenPattern = Pattern.compile("<media>.*?<url.*?><!\\[CDATA\\[(.+?)\\]\\]></url>.*?<urltoken.*?><!\\[CDATA\\[(.+?)\\]\\]></urltoken>.*?<urlidx.*?><!\\[CDATA\\[(.+?)\\]\\]></urlidx>.*?<urlenc.*?><!\\[CDATA\\[(.+?)\\]\\]></urlenc>.*?<urlenckey.*?><!\\[CDATA\\[(.+?)\\]\\]></urlenckey>.*?</media>", Pattern.DOTALL);
        Pattern mediaIdxPattern = Pattern.compile("<media>.*?<urlenckey.*?><!\\[CDATA\\[(.+?)\\]\\]></urlenckey>.*?</media>");
        Pattern timestampPattern = Pattern.compile("<createTime><!\\[CDATA\\[(.+?)\\]\\]></createTime>");

        Matcher userIdMatcher = userIdPattern.matcher(xmlResult);
        Matcher contentMatcher = contentPattern.matcher(xmlResult);
        Matcher mediaTokenMatcher = mediaTokenPattern.matcher(xmlResult);
        Matcher mediaIdxMatcher = mediaIdxPattern.matcher(xmlResult);
        Matcher timestampMatcher = timestampPattern.matcher(xmlResult);

        currentSns.id = getTimelineId(xmlResult);

        currentSns.rawXML = xmlResult;
        Log.d("wechatmomentstat", xmlResult);

        if (timestampMatcher.find()) {
            currentSns.timestamp = Integer.parseInt(timestampMatcher.group(1));
        }

        if (userIdMatcher.find()) {
            currentSns.authorId = userIdMatcher.group(1);
        }

        if (contentMatcher.find()) {
            currentSns.content = contentMatcher.group(1);
        }


        while (mediaTokenMatcher.find()) {
            String url = mediaTokenMatcher.group(1);

            String urltoken = mediaTokenMatcher.group(2);
            String urlidx = mediaTokenMatcher.group(3);
            String urlenc = mediaTokenMatcher.group(4);
            String urlenckey = mediaTokenMatcher.group(3);

            Log.d("wechatmomentstat", "url urlidx(thumbtoken)=" + urlidx + " ,urlenc=" + urlenc + " ,urlenckey=" + urlenckey);
            url = url + "?tp=jpg&token=" + urltoken + "&idx=1" + "&urlenc=" + urlenc + "&urlenckey=" + urlenckey;

            currentSns.mediaList.add(url);
        }

        while (mediaIdxMatcher.find()) {
            boolean flag = true;
            /*for (int i=0;i<currentSns.mediaList.size();i++) {
                if (currentSns.mediaList.get(i).equals(mediaIdxMatcher.group(1))) {
                    flag = false;
                    break;
                }
            }*/
            if (flag) {

                //Object snsObject = this.ModelClass.newInstance();
                //Method fromBinMethod = this.ModelClass.getMethod("FI", byte[].class);
                String url = mediaIdxMatcher.group(1);
                Log.d("wechatmomentstat", "urlidx=" + url);
                //url = (String)fromBinMethod.invoke(snsObject, url);
                //Log.d("wechatmomentstat", "url1="+url);
                //url = url.replace("wxpc","webp");
                //currentSns.mediaList.add(url);
            }
        }
        return currentSns;
    }

    static public void parseSnsObject(Object aqiObject, SnsInfo matchSns) throws Throwable {
        Field field = null;
        Object userId = null, nickname = null, ales = null;

        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_USERID_FIELD);
        userId = field.get(aqiObject);


        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_NICKNAME_FIELD);
        nickname = field.get(aqiObject);

        if (userId == null || nickname == null) {
            return;
        }

        matchSns.ready = true;
        matchSns.authorName = (String) nickname;
        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_COMMENTS_FIELD);
        LinkedList list = (LinkedList) field.get(aqiObject);
        for (int i = 0; i < list.size(); i++) {
            Object childObject = list.get(i);
            parseSnsObjectExt(childObject, true, matchSns);
        }

        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_LIKES_FIELD);
        LinkedList likeList = (LinkedList) field.get(aqiObject);
        for (int i = 0; i < likeList.size(); i++) {
            Object likeObject = likeList.get(i);
            parseSnsObjectExt(likeObject, false, matchSns);
        }

    }

    static public void parseSnsObjectExt(Object apzObject, boolean isComment, SnsInfo matchSns) throws Throwable {
        if (isComment) {
            Field field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_NAME_FIELD);
            Object authorName = field.get(apzObject);

            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_REPLY_TO_FIELD);
            Object replyToUserId = field.get(apzObject);

            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_COMMENT_FIELD);
            Object commentContent = field.get(apzObject);

            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_ID_FIELD);
            Object authorId = field.get(apzObject);

            if (authorId == null || commentContent == null || authorName == null) {
                return;
            }

            for (int i = 0; i < matchSns.comments.size(); i++) {
                SnsInfo.Comment loadedComment = matchSns.comments.get(i);
                if (loadedComment.authorId.equals((String) authorId) && loadedComment.content.equals((String) commentContent)) {
                    return;
                }
            }

            SnsInfo.Comment newComment = new SnsInfo.Comment();
            newComment.authorName = (String) authorName;
            newComment.content = (String) commentContent;
            newComment.authorId = (String) authorId;
            newComment.toUserId = (String) replyToUserId;

            SnsInfo.Like newLike = new SnsInfo.Like();
            newLike.userId = (String) authorId;
            newLike.userName = (String) authorName;
            if (Config.currentUserId.equals((String) matchSns.authorId)) {
                boolean skip = false;
                //Log.d("wechatmomentstat", "like me isCurrentUser ");
                for (int i = 0; i < matchSns.likeme.size(); i++) {
                    if (matchSns.likeme.get(i).userId.equals((String) authorId)) {
                        skip = true;
                    }
                }
                if (!skip)
                    matchSns.likeme.add(newLike);
            }


            for (int i = 0; i < matchSns.comments.size(); i++) {
                SnsInfo.Comment loadedComment = matchSns.comments.get(i);
                if (replyToUserId != null && loadedComment.authorId.equals((String) replyToUserId)) {
                    newComment.toUser = loadedComment.authorName;
                    break;
                }
            }

            matchSns.comments.add(newComment);
        } else {
            Field field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_NAME_FIELD);
            Object nickname = field.get(apzObject);
            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_ID_FIELD);
            Object userId = field.get(apzObject);
            if (nickname == null || userId == null) {
                return;
            }

            if (((String) userId).equals("")) {
                return;
            }

            SnsInfo.Like newLike = new SnsInfo.Like();
            newLike.userId = (String) userId;
            newLike.userName = (String) nickname;
            if (Config.currentUserId.equals((String) matchSns.authorId)) {
                boolean skip = false;
                for (int i = 0; i < matchSns.likeme.size(); i++) {
                    if (matchSns.likeme.get(i).userId.equals((String) userId)) {
                        skip = true;
                    }
                }
                if (!skip)
                    matchSns.likeme.add(newLike);
            }

            for (int i = 0; i < matchSns.likes.size(); i++) {
                if (matchSns.likes.get(i).userId.equals((String) userId)) {
                    return;
                }
            }
            matchSns.likes.add(newLike);

        }
    }

    static public String getTimelineId(String xmlResult) {
        Pattern idPattern = Pattern.compile("<id><!\\[CDATA\\[(.+?)\\]\\]></id>");
        Matcher idMatcher = idPattern.matcher(xmlResult);
        if (idMatcher.find()) {
            return idMatcher.group(1);
        } else {
            return "";
        }
    }
}
