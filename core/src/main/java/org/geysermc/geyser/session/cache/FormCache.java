/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.data.ModalFormCancelReason;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class FormCache {

    /**
     * The magnitude of this doesn't actually matter, but it must be negative so that
     * BedrockNetworkStackLatencyTranslator can detect the hack.
     */
    private static final long MAGIC_FORM_IMAGE_HACK_TIMESTAMP = -1234567890L;

    private final FormDefinitions formDefinitions = FormDefinitions.instance();
    private final AtomicInteger formIdCounter = new AtomicInteger(0);
    private final Int2ObjectMap<Form> forms = new Int2ObjectOpenHashMap<>();
    LinkedList<Integer> formsOrderList = new LinkedList<>();
    private final GeyserSession session;

    public int addForm(Form form) {
        int formId = formIdCounter.getAndIncrement();
        forms.put(formId, form);
        formsOrderList.add(formId);

        if (formsOrderList.size() > 50) {
            int removeFormId = formsOrderList.getFirst();
            ModalFormResponsePacket packet = new ModalFormResponsePacket();
            packet.setFormId(removeFormId);
            packet.setCancelReason(Optional.of(ModalFormCancelReason.USER_CLOSED));
            packet.setFormData(null);
            this.handleResponse(packet);
        }
        return formId;
    }

    public void showForm(Form form) {
        int formId = addForm(form);

        if (session.getUpstream().isInitialized()) {
            sendForm(formId, form);
        }
    }

    private void sendForm(int formId, Form form) {
        String jsonData = formDefinitions.codecFor(form).jsonData(form);

        ModalFormRequestPacket formRequestPacket = new ModalFormRequestPacket();
        formRequestPacket.setFormId(formId);
        formRequestPacket.setFormData(jsonData);
        session.sendUpstreamPacket(formRequestPacket);

        // Hack to fix the (url) image loading bug
        if (form instanceof SimpleForm) {
            NetworkStackLatencyPacket latencyPacket = new NetworkStackLatencyPacket();
            latencyPacket.setFromServer(true);
            latencyPacket.setTimestamp(MAGIC_FORM_IMAGE_HACK_TIMESTAMP);
            session.scheduleInEventLoop(
                    () -> session.sendUpstreamPacket(latencyPacket),
                    500, TimeUnit.MILLISECONDS
            );
        }
    }

    public void resendAllForms() {
        for (Int2ObjectMap.Entry<Form> entry : forms.int2ObjectEntrySet()) {
            sendForm(entry.getIntKey(), entry.getValue());
        }
    }

    public void handleResponse(ModalFormResponsePacket response) {
        Form form = forms.remove(response.getFormId());
        formsOrderList.remove((Integer) response.getFormId());
        if (form == null) {
            return;
        }

        try {
            formDefinitions.definitionFor(form)
                    .handleFormResponse(form, response.getFormData());
        } catch (Exception e) {
            GeyserImpl.getInstance().getLogger().error("Error while processing form response!", e);
        }
    }
}
