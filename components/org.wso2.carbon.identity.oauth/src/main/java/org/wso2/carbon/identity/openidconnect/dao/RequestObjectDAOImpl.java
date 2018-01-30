/*
 *
 *   Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 * /
 */

package org.wso2.carbon.identity.openidconnect.dao;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationManagementUtil;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.oauth.IdentityOAuthAdminException;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.dao.AuthorizationCodeDAOImpl;

import org.wso2.carbon.identity.oauth2.dao.OAuthTokenPersistenceFactory;
import org.wso2.carbon.identity.openidconnect.OIDCConstants;
import org.wso2.carbon.identity.openidconnect.model.RequestedClaim;
import org.wso2.carbon.utils.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.wso2.carbon.identity.oauth.OAuthUtil.handleError;

/**
 * This class handles all the DAO layer activities which are related to OIDC request object.
 */
public class RequestObjectDAOImpl implements RequestObjectDAO {

    private static final String ID = "ID";
    private final Log log = LogFactory.getLog(AuthorizationCodeDAOImpl.class);

    /**
     * Store request object related data into related db tables.
     *
     * @param consumerKey    consumer key
     * @param sessionDataKey session data key
     * @param claims         request object claims
     * @throws IdentityOAuth2Exception
     */
    @Override
    public void insertRequestObjectData(String consumerKey, String sessionDataKey, List<List<RequestedClaim>> claims)
            throws IdentityOAuth2Exception {

        int requestObjectId = -1;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String sqlStmt = SQLQueries.STORE_IDN_OIDC_REQ_OBJECT_REFERENCE;
        Connection connection = null;
        try {
            connection = IdentityDatabaseUtil.getDBConnection();
            String dbProductName = connection.getMetaData().getDatabaseProductName();
            prepStmt = connection.prepareStatement(sqlStmt, new String[]{
                    DBUtils.getConvertedAutoGeneratedColumnName(dbProductName, ID)});
            prepStmt.setString(1, sessionDataKey);
            prepStmt.setString(2, consumerKey);
            prepStmt.execute();
            rs = prepStmt.getGeneratedKeys();
            if (rs.next()) {
                requestObjectId = rs.getInt(1);
            }
            connection.commit();
            if (requestObjectId != -1) {
                if (log.isDebugEnabled()) {
                    log.debug("Successfully stored the request object reference: " + requestObjectId);
                }
                insertRequestObjectClaims(requestObjectId, claims, connection);
            }
        } catch (SQLException e) {

            String errorMessage = "Error when storing the request object reference";
            log.error(errorMessage, e);
            throw new IdentityOAuth2Exception(errorMessage, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, rs, prepStmt);
        }
    }

    /**
     * Update request object reference when the code or the token is generated.
     *
     * @param sessionDataKey session data key
     * @param codeId         code id
     * @throws IdentityOAuth2Exception
     */
    @Override
    public void updateRequestObjectReferencebyCodeId(String sessionDataKey, String codeId) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement ps = null;
        try {
            connection.setAutoCommit(false);
            String sql = SQLQueries.UPDATE_REQUEST_OBJECT;
            ps = connection.prepareStatement(sql);
            ps.setString(1, codeId);
            ps.setString(2, null);
            ps.setString(3, sessionDataKey);
            ps.execute();
            connection.commit();
        } catch (SQLException e) {
            String errorMsg = "Can not update code id or the access token id of the table ."
                    + OIDCConstants.IDN_OIDC_REQ_OBJECT_REFERENCE;
            throw new IdentityOAuth2Exception(errorMsg, e);

        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, ps);
        }
    }

    /**
     * Update request object reference when the code or the token is generated.
     *
     * @param sessionDataKey session data key
     * @param accessTokenId  access token id
     * @throws IdentityOAuth2Exception
     */
    @Override
    public void updateRequestObjectReferencebyTokenId(String sessionDataKey, String accessTokenId) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement ps = null;
        try {
            connection.setAutoCommit(false);
            String sql = SQLQueries.UPDATE_REQUEST_OBJECT;
            ps = connection.prepareStatement(sql);
            ps.setString(1, null);
            ps.setString(2, accessTokenId);
            ps.setString(3, sessionDataKey);
            ps.execute();
            connection.commit();
        } catch (SQLException e) {
            String errorMsg = "Can not update code id or the access token id of the table ."
                    + OIDCConstants.IDN_OIDC_REQ_OBJECT_REFERENCE;
            throw new IdentityOAuth2Exception(errorMsg, e);

        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, ps);
        }
    }

    private void insertRequestObjectClaims(int requestObjectId, List<List<RequestedClaim>> claims, Connection connection)
            throws IdentityOAuth2Exception {

        int requestObjectClaimId = -1;
        String sqlStmt = SQLQueries.STORE_IDN_OIDC_REQ_OBJECT_CLAIMS;
        ResultSet rs;
        PreparedStatement prepStmt = null;
        try {
            String dbProductName = connection.getMetaData().getDatabaseProductName();
            prepStmt = connection.prepareStatement(sqlStmt, new String[]{
                    DBUtils.getConvertedAutoGeneratedColumnName(dbProductName, ID)});
            for (List<RequestedClaim> list : claims) {
                for (RequestedClaim claim : list) {
                    prepStmt.setInt(1, requestObjectId);
                    prepStmt.setString(2, claim.getName());
                    prepStmt.setBoolean(3, claim.isEssential());
                    prepStmt.setString(4, claim.getValue());
                    if (OIDCConstants.USERINFO.equals(claim.getType())) {
                        prepStmt.setBoolean(5, true);
                    } else if (OIDCConstants.ID_TOKEN.equals(claim.getType())) {
                        prepStmt.setBoolean(5, false);
                    }
                    prepStmt.addBatch();
                    prepStmt.executeBatch();
                    rs = prepStmt.getGeneratedKeys();
                    if (rs.next()) {
                        requestObjectClaimId = rs.getInt(1);
                    }
                    connection.commit();
                    if (requestObjectClaimId > -1) {
                        if (log.isDebugEnabled()) {
                            log.debug("Successfully stored the request object claims in " + OIDCConstants.
                                    IDN_OIDC_REQ_OBJECT_CLAIMS + "table.");
                        }
                        if (CollectionUtils.isNotEmpty(claim.getValues()) && claim.getValues().size() > 0) {
                            insertRequestObjectClaimValues(requestObjectClaimId, claim.getValues(), connection);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            String errorMessage = "Error when storing the request object claims.";
            log.error(errorMessage, e);
            throw new IdentityOAuth2Exception(errorMessage, e);
        } finally {
            IdentityApplicationManagementUtil.closeStatement(prepStmt);
        }
    }

    private void insertRequestObjectClaimValues(int requestObjectClaimId, List<String> values, Connection connection)
            throws IdentityOAuth2Exception {

        String sqlStmt = SQLQueries.STORE_IDN_OIDC_REQ_OBJECT_CLAIM_VALUES;
        PreparedStatement prepStmt = null;
        try {
            prepStmt = connection.prepareStatement(sqlStmt);

            for (String value : values) {
                prepStmt.setInt(1, requestObjectClaimId);
                prepStmt.setString(2, value);
                prepStmt.addBatch();
            }
            prepStmt.executeBatch();
            connection.commit();

        } catch (SQLException e) {
            String errorMessage = "Error when storing the request object claim values.";
            log.error(errorMessage, e);
            throw new IdentityOAuth2Exception(errorMessage, e);
        } finally {
            IdentityApplicationManagementUtil.closeStatement(prepStmt);
        }
    }

    /**
     * Retrieve Requested claims for the id token and user info endpoint.
     *
     * @param token    token
     * @param isUserInfo return true if the claims are requested from user info end point.
     * @return
     * @throws IdentityOAuth2Exception
     */
    @Override
    public List<RequestedClaim> getRequestedClaims(String token, boolean isUserInfo) throws IdentityOAuth2Exception {
        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement prepStmt = null;
        ResultSet resultSet = null;
        List<RequestedClaim> essentialClaims = new ArrayList<>();
        try {
            String sql = SQLQueries.RETRIEVE_REQUESTED_CLAIMS_BY_TOKEN;
            String tokenId = OAuthTokenPersistenceFactory.getInstance().getAccessTokenDAO().
                    getTokenIdByAccessToken(token);

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, tokenId);
            prepStmt.setBoolean(2, isUserInfo);
            resultSet = prepStmt.executeQuery();

            while (resultSet.next()) {
                RequestedClaim requestedClaim = new RequestedClaim();
                requestedClaim.setName(resultSet.getString(1));
                requestedClaim.setEssential(resultSet.getBoolean(2));
                requestedClaim.setValue(resultSet.getString(3));
                essentialClaims.add(requestedClaim);
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMsg = "Error occurred while retrieving request object.";
            throw new IdentityOAuth2Exception(errorMsg, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, resultSet, prepStmt);
        }
        return essentialClaims;
    }

    @Override
    public void refreshRequestObjectReference(String oldAccessTokenId, String newAccessTokenId)
            throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement ps = null;
        try {
            connection.setAutoCommit(false);
            String sql = SQLQueries.REFRESH_REQUEST_OBJECT;
            ps = connection.prepareStatement(sql);
            ps.setString(1, newAccessTokenId);
            ps.setString(2, oldAccessTokenId);
            ps.execute();
            connection.commit();
        } catch (SQLException e) {
            String errorMsg = "Can not update refreshed token id of the table ."
                    + OIDCConstants.IDN_OIDC_REQ_OBJECT_REFERENCE;
            throw new IdentityOAuth2Exception(errorMsg, e);

        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, ps);
        }
    }

    @Override
    public void updateRequestObjectReferenceCodeToToken(String codeId, String tokenId) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement ps = null;
        try {
            connection.setAutoCommit(false);
            deleteRequestObjectReferenceforCode(tokenId);
            String sql = SQLQueries.UPDATE_REQUEST_OBJECT_TOKEN_FOR_CODE;
            ps = connection.prepareStatement(sql);
            ps.setString(1, tokenId);
            ps.setString(2, codeId);
            ps.execute();
            connection.commit();
        } catch (SQLException e) {
            String errorMsg = "Can not update token id for code id: " + codeId;
            throw new IdentityOAuth2Exception(errorMsg, e);

        } catch (IdentityOAuthAdminException e) {
            String errorMsg = "Can not delete existing entry for the same token id" + tokenId;
            throw new IdentityOAuth2Exception(errorMsg, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, ps);
        }
    }

    private void deleteRequestObjectReferenceforCode(String tokenId) throws IdentityOAuthAdminException {

        try (Connection connection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement prepStmt = connection.prepareStatement(SQLQueries.DELETE_REQ_OBJECT_TOKEN_FOR_CODE)) {
            prepStmt.setString(1, tokenId);
            prepStmt.execute();
            connection.commit();
        } catch (SQLException e) {
            throw handleError("Can not delete existing entry for the same token id" + tokenId, e);
        }
    }

    public void deleteRequestObjectReferenceByTokenId(String tokenId) throws IdentityOAuthAdminException {

        try (Connection connection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement prepStmt = connection.prepareStatement(SQLQueries.DELETE_REQ_OBJECT_BY_TOKEN_ID)) {
            prepStmt.setString(1, tokenId);
            prepStmt.execute();
            connection.commit();
        } catch (SQLException e) {
            throw handleError("Error when executing the SQL : " + SQLQueries.DELETE_REQ_OBJECT_BY_TOKEN_ID, e);
        }
    }

    public void deleteRequestObjectReferenceByCode(String codeId) throws IdentityOAuthAdminException {

        try (Connection connection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement prepStmt = connection.prepareStatement(SQLQueries.DELETE_REQ_OBJECT_BY_CODE_ID)) {
            prepStmt.setString(1, codeId);
            prepStmt.execute();
            connection.commit();
        } catch (SQLException e) {
            throw handleError("Error when executing the SQL : " + SQLQueries.DELETE_REQ_OBJECT_BY_CODE_ID, e);
        }
    }
}
