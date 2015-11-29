package org.droidplanner.android.utils;

import java.io.IOException;
import java.net.URL;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.*;

/**
 * Created by pritam on 29/11/15.
 */
public class SocketConnector {
//variables
        public enum NodeCaps {
            None(0x00)        ,
            PiCam(0x01)       ,
            Thermal(0x10)     ,
            MAVProxy(0x100)   ;

            private int numVal;

            NodeCaps(int numVal) {
                this.numVal = numVal;
            }

            public int getNumVal() {
                return numVal;
            }
        }

    public int m_currentIndex;
    public int caps;

    DatagramSocket m_socket;
    byte [] message  = new byte [1500];
    DatagramPacket p = new DatagramPacket (message,message.length);
    //Functions
    public void NodeSelector()
    {
        try {
            m_socket =  new DatagramSocket(31311);
            SocketAddress sockaddr = m_socket.getLocalSocketAddress();
            m_socket.bind(sockaddr);
        }
        catch (SocketException e) {
            e.printStackTrace();
        }
    }
    public void datagramReceived()
    {
        try {//TODO resize p if needed
            m_socket.receive(p);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (message.toString().startsWith("raspberry")) {
            if (message.toString().contains("picam")) {
                caps |= NodeCaps.PiCam.getNumVal();
            }

            if (message.toString().contains("thermal")) {
                caps |= NodeCaps.Thermal.getNumVal();
            }

            if (message.toString().contains("mavproxy")) {
                caps |= NodeCaps.MAVProxy.getNumVal();
            }
         /*   int targetStreamingPort = 5003 + m_discoveredNodes.count();
            address = addr;
            addressString = addr.toString().split(":").last();
            onNodeDiscovered(node);

                m_discoveredNodes << node;
                Q_EMIT nodeDiscovered(node);*/
        }
    }


    public void terminatePicam()
    {
        try {
            URL terminateUrl = new URL("http://192.168.42.1:8080/picam/?command=terminate");
            HttpURLConnection urlConnection = (HttpURLConnection) terminateUrl.openConnection();
            if (urlConnection.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error code : "+ urlConnection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void terminateThermal()
    {
        try {
            URL terminateUrl = new URL("http://192.168.42.1:8080/thermalcam/?command=terminate");
            HttpURLConnection urlConnection = (HttpURLConnection) terminateUrl.openConnection();
            if (urlConnection.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error code : "+ urlConnection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdownAll()
    {
        try {
            URL terminateUrl = new URL("http://192.168.42.1:8080/os_command/?command=sudo halt");
            HttpURLConnection urlConnection = (HttpURLConnection) terminateUrl.openConnection();
            if (urlConnection.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error code : "+ urlConnection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void restartAll()
    {
        try {
            URL terminateUrl = new URL("http://192.168.42.1:8080/os_command/?command=sudo reboot");
            HttpURLConnection urlConnection = (HttpURLConnection) terminateUrl.openConnection();
            if (urlConnection.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error code : "+ urlConnection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void terminateMavProxy()
    {
        try {
            URL terminateUrl = new URL("http://192.168.42.1:8080/mavproxy/?command=screen -X -S MAVPROXY quit");
            HttpURLConnection urlConnection = (HttpURLConnection) terminateUrl.openConnection();
            if (urlConnection.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error code : "+ urlConnection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void  startStreaming()
    {
        URL urlconn = new URL("http://192.168.42.1:8080/picam/?command=raspivid -t 0 -vf -hf $OPT_STRING -o - | gst-launch-1.0 -v fdsrc ! h264parse ! rtph264pay config-interval=1 pt=96 ! udpsink host=$CLIENT_IP port=$UDP_PORT");
        servercmd = servercmd.replace("$SERVER_IP", node.addressString);
        servercmd = servercmd.replace("$CLIENT_IP", deviceAddress());
        servercmd = servercmd.replace("$UDP_PORT", QString::number(node.targetStreamingPort));
        servercmd = servercmd.replace("$OPT_STRING", optionsString);
        qDebug() << "SERVER CMD: " << servercmd;
        QVariantMap map;
        map.insert("requestFor", PiNode::PiCam);
        map.insert("nodeIndex", m_currentIndex);
        sendRequest(servercmd, map);
    }

    public void stopStreaming()
    {
        terminatePicam();
    }

    bool NodeSelector::startThermal(int nodeIndex)
    {
        PiNodeList nodes = m_discoverer->discoveredNodes();
        if (nodes.count() < nodeIndex -1) {
            // out of range
            return false;
        }

        return startThermal(nodes.at(nodeIndex));
    }

    bool NodeSelector::startThermal(const PiNode &node)
    {
        if (node.caps & PiNode::Thermal) {
            if (!(node.capsRunning & PiNode::Thermal)) {
                QUrl startUrl("http://" + node.addressString + ":8080/thermalcam/?command=start");
                qDebug() << "thermal server start url " << startUrl;
                QVariantMap map;
                map.insert("requestFor", PiNode::Thermal);
                map.insert("nodeIndex", m_currentIndex);
//                map.insert("camUrl", mjpegUrl);
                sendRequest(startUrl, map);
            }
            return true;
        }
        return false;
    }

    void NodeSelector::terminateThermal(int index)
    {
        PiNodeList nodes = m_discoverer->discoveredNodes();
        if (nodes.count() < index -1) {
            // out of range
            return;
        }

        terminateThermal(nodes.at(index));
    }

    QString NodeSelector::deviceAddress() const
    {
        QList<QHostAddress> addresses = QNetworkInterface::allAddresses();
        QHostAddress addr;
        Q_FOREACH(QHostAddress address, addresses) {
        if (address.isLoopback()) {
            continue;
        }
        addr = address;
        break;
    }

        QString address = addr.toString();
        qDebug() << "got the following address for the host device" << address;
        return address;
    }

    void NodeSelector::replyFinished()
    {
        QNetworkReply *reply = qobject_cast<QNetworkReply*>(sender());
        reply->deleteLater();
        PiNodeList nodes = m_discoverer->discoveredNodes();
        if (!nodes.size()) {
            return;
        }
        Q_ASSERT(reply);
        if (reply->error() == QNetworkReply::NoError)
        {
            bool ok = false;
            int capibility = reply->property("requestFor").toInt(&ok);
            if (!ok) {
                qDebug() << "generic request return";
                return;
            }

            int index;
            QUrl url;
            switch (capibility) {
                case PiNode::PiCam:
                    index = reply->property("nodeIndex").toInt();
                    nodes[index].capsRunning |= PiNode::PiCam;
                    qDebug() << "picam started without any error";
                    break;
                case PiNode::Thermal:
                    index = reply->property("nodeIndex").toInt();
                    nodes[index].capsRunning |= PiNode::Thermal;
//            url = reply->property("camUrl").toUrl();
//            Q_EMIT thermalUrl(url);
                    qDebug() << "thermal camera started without any error";
                    break;
                case PiNode::MAVProxy:
                    index = reply->property("nodeIndex").toInt();
                    nodes[index].capsRunning |= PiNode::MAVProxy;
                    qDebug() << "mavproxy started without any error";
                default:
                    break;
            }
        }
    }

    void NodeSelector::sendRequest(const QUrl &url, const QVariantMap &properties)
    {
        QNetworkRequest request(url);
        QNetworkReply *reply = m_nam->get(request);
        Q_FOREACH(const QString &key, properties.keys()) {
        reply->setProperty(key.toStdString().c_str(), properties.value(key));
    }
        connect(reply, SIGNAL(finished()), this, SLOT(replyFinished()));
    }
}
